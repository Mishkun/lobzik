package xyz.mishkun.lobzik.graph

import org.gephi.appearance.api.AppearanceController
import org.gephi.appearance.api.PartitionFunction
import org.gephi.appearance.plugin.PartitionElementColorTransformer
import org.gephi.appearance.plugin.palette.PaletteManager
import org.gephi.filters.api.FilterController
import org.gephi.filters.api.Query
import org.gephi.filters.api.Range
import org.gephi.filters.plugin.AbstractFilter
import org.gephi.filters.plugin.attribute.AttributeEqualBuilder.EqualBooleanFilter
import org.gephi.filters.plugin.attribute.AttributeRangeBuilder
import org.gephi.filters.plugin.graph.GiantComponentBuilder
import org.gephi.filters.plugin.partition.PartitionBuilder.NodePartitionFilter
import org.gephi.filters.spi.NodeFilter
import org.gephi.graph.api.*
import org.gephi.io.exporter.api.ExportController
import org.gephi.io.exporter.preview.SVGExporter
import org.gephi.io.exporter.spi.GraphExporter
import org.gephi.io.importer.api.Container
import org.gephi.io.importer.api.EdgeDirectionDefault
import org.gephi.io.importer.api.ImportController
import org.gephi.io.processor.plugin.MergeProcessor
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2
import org.gephi.layout.plugin.labelAdjust.LabelAdjust
import org.gephi.preview.api.PreviewController
import org.gephi.preview.api.PreviewProperty
import org.gephi.project.api.ProjectController
import org.gephi.statistics.plugin.Degree
import org.gephi.statistics.plugin.Modularity
import org.gephi.statistics.plugin.PageRank
import org.openide.util.Lookup
import java.io.File
import java.io.IOException
import kotlin.math.ceil


class GraphRoutine(
    val monolithModule: String,
    val featureModules: List<String>,
    val nodesFile: File,
    val edgesFile: File,
    val outputDir: File,
) {
    fun doAnalysis() {
        //Init a project - and therefore a workspace
        val pc = Lookup.getDefault().lookup(ProjectController::class.java)
        pc.newProject()
        val workspace = pc.currentWorkspace
        //Get controllers and models
        val importController = Lookup.getDefault().lookup(ImportController::class.java)
        //Import file
        val nodesContainer: Container = importController.importContainer(nodesFile) ?: return
        val edgesContainer: Container = importController.importContainer(edgesFile) ?: return
        //Append imported data to GraphAPI
        importController.process(arrayOf(nodesContainer, edgesContainer), MergeProcessor(), workspace)

        val graphModel: GraphModel = Lookup.getDefault().lookup(GraphController::class.java).graphModel

//See if graph is well imported
        val graph: DirectedGraph = graphModel.directedGraph
        println("Nodes: " + graph.nodeCount)
        println("Edges: " + graph.edgeCount)

        val appearanceController = Lookup.getDefault().lookup(AppearanceController::class.java)
        val appearanceModel = appearanceController.model

        val filterController: FilterController = Lookup.getDefault().lookup(FilterController::class.java)
//Filter Giant Component
        val giantComponentFilter = GiantComponentBuilder.GiantComponentFilter()
        giantComponentFilter.init(graph)
        val giantComponentQuery = filterController.createQuery(giantComponentFilter)

        val interfaceColumn = graphModel.nodeTable.getColumn("isinterface")
        val interfaceFilter = EqualBooleanFilter.Node(interfaceColumn)
        interfaceFilter.isMatch = false
        val interfaceQuery = filterController.createQuery(interfaceFilter)
        filterController.setSubQuery(giantComponentQuery,interfaceQuery)

        val projectColumn = graphModel.nodeTable.getColumn("module")
        val projFunc = appearanceModel.getNodeFunction(projectColumn, PartitionElementColorTransformer::class.java)
        val projPartition = (projFunc as PartitionFunction).partition
        val projFilter = NodePartitionFilter(appearanceModel, projPartition)
        val selected = projFilter.partition.getValues(graph).filter { value ->
            value.toString() == monolithModule || featureModules.map { it.toRegex() }.any { it.matches(value.toString()) }
        }
        projFilter.parts = selected.toSet()
        projFilter.init(graph)
        val partitionProjectQuery: Query = filterController.createQuery(projFilter)
        filterController.setSubQuery(interfaceQuery, partitionProjectQuery)

        graphModel.visibleView = filterController.filter(giantComponentQuery)


//PageRank
        val pageRankAlgo = PageRank()
        pageRankAlgo.directed = true
        pageRankAlgo.execute(graphModel)

        val column = graphModel.nodeTable.getColumn(PageRank.PAGERANK)
        val pagerankFilter = AttributeRangeBuilder.AttributeRangeFilter.Node(column)
        val cap = graphModel.nodeIndex.values(column).map { it as Double }.sorted()
            .run { elementAt(ceil(size * 0.95).toInt()) }
        pagerankFilter.init(graph)
        pagerankFilter.range = Range(0.0, cap)

        val pageRankQuery = filterController.createQuery(pagerankFilter)
        filterController.setSubQuery(partitionProjectQuery, pageRankQuery)
        graphModel.visibleView = filterController.filter(giantComponentQuery)


//See visible graph stats

        filterController.exportToColumn("isModularized", giantComponentQuery)
//See visible graph stats
        val graphVisible = graphModel.undirectedGraphVisible
        println("Nodes: " + graphVisible.nodeCount)
        println("Edges: " + graphVisible.edgeCount)

//Run YifanHuLayout for 100 passes - The layout always takes the current visible view

//Run YifanHuLayout for 100 passes - The layout always takes the current visible view
        ForceAtlas2(null).also { layout ->
            layout.setGraphModel(graphModel)
            layout.resetPropertiesValues()
            layout.initAlgo()
            var i = 0
            while (i < 1000 && layout.canAlgo()) {
                layout.goAlgo()
                i += 1
            }
            layout.endAlgo()
        }

//Get Modularity And Degree
        val modularity = Modularity()
        modularity.useWeight = true
        modularity.execute(graphModel)

        val degree = Degree()
        degree.execute(graphModel)

//Rank color by Modularity Class
        val modColumn = graphModel.nodeTable.getColumn(Modularity.MODULARITY_CLASS)
        val func = appearanceModel.getNodeFunction(modColumn, PartitionElementColorTransformer::class.java)
        val partition = (func as PartitionFunction).partition
        println(partition.size(graph).toString() + " partitions found")
        val palette = PaletteManager.getInstance().randomPalette(partition.size(graph))
        partition.setColors(graph, palette.colors)
        appearanceController.transform(func)


        val visibleGraph = graphModel.directedGraphVisible
        val modules = visibleGraph.nodes.groupBy { it.getAttribute(Modularity.MODULARITY_CLASS) as Int }
        val moduleLabels = modules.mapValues { (_, nodes) -> nodes.maxBy { it.getAttribute(Degree.DEGREE) as Int }.label }
        //Rank by conductance
        val modulesConductance = modules.mapValues { (thisCommunity, nodes) ->
            val (intraEdges, extraEdges) = nodes.flatMap { node -> visibleGraph.getOutEdges(node) }.partition { it.target.getAttribute(Modularity.MODULARITY_CLASS) == thisCommunity }
            intraEdges.count().toDouble() / (intraEdges.count() + extraEdges.count())
        }

//Preview

//Preview
        val previewModel = Lookup.getDefault().lookup(PreviewController::class.java).model
        previewModel.properties.putValue(PreviewProperty.SHOW_NODE_LABELS, true)
        previewModel.properties.putValue(PreviewProperty.ARROW_SIZE, 0.0)
        previewModel.properties.putValue(PreviewProperty.EDGE_RESCALE_WEIGHT, true)
        previewModel.properties.putValue(PreviewProperty.EDGE_RESCALE_WEIGHT_MIN, 1.0)
        previewModel.properties.putValue(PreviewProperty.EDGE_RESCALE_WEIGHT_MAX, 2.0)
        previewModel.properties.putValue(
            PreviewProperty.NODE_LABEL_FONT,
            previewModel.properties.getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(8)
        )

//Export

//Export
        val ec = Lookup.getDefault().lookup(ExportController::class.java)
        val exporter = ec.getExporter("gexf") as GraphExporter //Get GEXF exporter
        exporter.isExportVisible = true //Only exports the visible (filtered) graph
        exporter.workspace = workspace
        try {
            ec.exportFile(File(outputDir, "io_gexf.gexf"), exporter)
        } catch (ex: IOException) {
            ex.printStackTrace()
            return
        }


        //Export to Writer
        val exporterGraphML = ec.getExporter("graphml") //Get GraphML exporter
        exporterGraphML.workspace = workspace
        try {
            ec.exportFile(File(outputDir, "io_graphml.graphml"), exporterGraphML)
        } catch (ex: IOException) {
            ex.printStackTrace()
            return
        }

        val svgExporter = ec.getExporter("svg") as SVGExporter
        svgExporter.workspace = workspace
        try {
            ec.exportFile(File(outputDir, "whole_graph.svg"), svgExporter)
        } catch (ex: IOException) {
            ex.printStackTrace()
            return
        }

        for ((idx, module) in modulesConductance.keys.sortedBy { modulesConductance[it] }.withIndex()) {
            println("started exporting ${moduleLabels[module]} [$idx/${modules.keys.size}]")
            val nodeSet = modules[module].orEmpty().toSet()
            println("nodeSet contains ${nodeSet.size} classes")
            val filter = NodeCollectionFilter(nodeSet)
            val query2 = filterController.createQuery(projFilter)
            val query = filterController.createQuery(filter)
            filterController.setSubQuery(query2, query)
            graphModel.visibleView = filterController.filter(query2)
            ForceAtlas2(null).also { layout ->
                layout.setGraphModel(graphModel)
                layout.resetPropertiesValues()
                layout.isNormalizeEdgeWeights = true
                layout.initAlgo()
                var i = 0
                while (i < 100 && layout.canAlgo()) {
                    layout.goAlgo()
                    i += 1
                }
                layout.endAlgo()
            }
            LabelAdjust(null).apply {
                setGraphModel(graphModel)
                initAlgo()
                goAlgo()
                endAlgo()
            }
            //PNG Exporter config and export to Byte array
            val svgExporter = ec.getExporter("svg") as SVGExporter
            svgExporter.workspace = workspace
            try {
                ec.exportFile(File(outputDir, "${idx}_${moduleLabels[module]}_${modulesConductance[module]}.svg"), svgExporter)
            } catch (ex: IOException) {
                ex.printStackTrace()
                return
            }
            filterController.remove(query)
            filterController.remove(query2)
        }
    }

    private fun ImportController.importContainer(file: File): Container? {
        val container: Container
        try {
            container = importFile(file)
            container.loader.setEdgeDefault(EdgeDirectionDefault.DIRECTED) //Force DIRECTED
            container.loader.setAllowAutoNode(false) //Don't create missing nodes
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }
        return container
    }

    private class NodeCollectionFilter(private val nodes: Set<Node>) : NodeFilter, AbstractFilter("nodeCollection") {
        private lateinit var neighbourhood: Set<Node>
        override fun init(graph: Graph): Boolean {
            neighbourhood = nodes.flatMapTo(HashSet()) { node -> graph.getEdges(node).flatMap { listOf(it.source, it.target) } }
                .subtract(nodes)
            println("Initialized node collection filter with ${nodes.size} nodes and ${neighbourhood.size} in neighb")
            return graph.nodeCount != 0
        }

        override fun evaluate(graph: Graph, node: Node): Boolean {
            return node in nodes || node in neighbourhood
        }

        override fun finish() {

        }

    }
}
