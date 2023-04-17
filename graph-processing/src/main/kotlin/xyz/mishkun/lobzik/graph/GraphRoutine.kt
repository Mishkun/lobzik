package xyz.mishkun.lobzik.graph

import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.summary
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul
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
import org.gephi.graph.api.DirectedGraph
import org.gephi.graph.api.Graph
import org.gephi.graph.api.GraphController
import org.gephi.graph.api.GraphModel
import org.gephi.graph.api.Node
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
import org.gephi.project.api.Workspace
import org.gephi.statistics.plugin.Degree
import org.gephi.statistics.plugin.Modularity
import org.gephi.statistics.plugin.PageRank
import org.openide.util.Lookup
import java.io.File
import java.io.IOException
import java.io.StringWriter
import kotlin.math.ceil
import kotlin.math.log2


class GraphRoutine(
    val monolithModule: String,
    val featureModules: List<String>,
    val nodesFile: File,
    val edgesFile: File,
    val outputDir: File,
) {
    fun doAnalysis() {
        // Init a project - and therefore a workspace
        val pc = Lookup.getDefault().lookup(ProjectController::class.java)
        pc.newProject()
        val workspace = pc.currentWorkspace
        // Get controllers and models
        val importController = Lookup.getDefault().lookup(ImportController::class.java)
        // Import file
        val nodesContainer: Container = importController.importContainer(nodesFile) ?: return
        val edgesContainer: Container = importController.importContainer(edgesFile) ?: return
        // Append imported data to GraphAPI
        importController.process(arrayOf(nodesContainer, edgesContainer), MergeProcessor(), workspace)

        val graphModel: GraphModel = Lookup.getDefault().lookup(GraphController::class.java).graphModel

// See if graph is well imported
        val graph: DirectedGraph = graphModel.directedGraph
        println("Nodes: " + graph.nodeCount)
        println("Edges: " + graph.edgeCount)

        val appearanceController = Lookup.getDefault().lookup(AppearanceController::class.java)
        val appearanceModel = appearanceController.model

        val filterController: FilterController = Lookup.getDefault().lookup(FilterController::class.java)
// Filter Giant Component
        val giantComponentFilter = GiantComponentBuilder.GiantComponentFilter()
        giantComponentFilter.init(graph)
        val giantComponentQuery = filterController.createQuery(giantComponentFilter)

        val interfaceColumn = graphModel.nodeTable.getColumn("isinterface")
        val interfaceFilter = EqualBooleanFilter.Node(interfaceColumn)
        interfaceFilter.isMatch = false
        val interfaceQuery = filterController.createQuery(interfaceFilter)
        filterController.setSubQuery(giantComponentQuery, interfaceQuery)

        val projectColumn = graphModel.nodeTable.getColumn("module")
        val projFunc = appearanceModel.getNodeFunction(projectColumn, PartitionElementColorTransformer::class.java)
        val projPartition = (projFunc as PartitionFunction).partition
        val projFilter = NodePartitionFilter(appearanceModel, projPartition)
        val selected = projFilter.partition.getValues(graph).map { it.toString() }.filter(::filterMonolithOrFeatures)
        projFilter.parts = selected.toSet()
        projFilter.init(graph)
        val partitionProjectQuery: Query = filterController.createQuery(projFilter)
        filterController.setSubQuery(interfaceQuery, partitionProjectQuery)

        graphModel.visibleView = filterController.filter(giantComponentQuery)

// PageRank
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

// See visible graph stats

        filterController.exportToColumn("isModularized", giantComponentQuery)
// See visible graph stats
        val graphVisible = graphModel.undirectedGraphVisible
        println("Nodes: " + graphVisible.nodeCount)
        println("Edges: " + graphVisible.edgeCount)

// Run YifanHuLayout for 100 passes - The layout always takes the current visible view

// Run YifanHuLayout for 100 passes - The layout always takes the current visible view
        ForceAtlas2(null).also { layout ->
            layout.setGraphModel(graphModel)
            layout.resetPropertiesValues()
            layout.initAlgo()
            var i = 0
            while (i < 100 && layout.canAlgo()) {
                layout.goAlgo()
                i += 1
            }
            layout.endAlgo()
        }

// Get Modularity And Degree
        val modularity = Modularity()
        modularity.useWeight = true
        modularity.execute(graphModel)

        val degree = Degree()
        degree.execute(graphModel)

// Rank color by Modularity Class
        val modColumn = graphModel.nodeTable.getColumn(Modularity.MODULARITY_CLASS)
        val func = appearanceModel.getNodeFunction(modColumn, PartitionElementColorTransformer::class.java)
        val partition = (func as PartitionFunction).partition
        println(partition.size(graph).toString() + " partitions found")
        val palette = PaletteManager.getInstance().randomPalette(partition.size(graph))
        partition.setColors(graph, palette.colors)
        appearanceController.transform(func)

        // extract module nodes from graph model
        val modules = graphModel.directedGraphVisible.nodes.groupBy { it.getAttribute(Modularity.MODULARITY_CLASS) as Int }
        // Execute tf-idf on module labels treating modules as documents
        val tf = modules.mapValues { (_, nodes) ->
            nodes.flatMap { it.label.split("(?<=.)(?=\\p{Upper})".toRegex()) }
                .fold(mutableMapOf<String, Int>()) { acc, el -> acc.merge(el, 1, Int::plus); acc }
        }
        val idf = modules.mapValues { (_, nodes) ->
            nodes.flatMap { it.label.split("(?<=.)(?=\\p{Upper})".toRegex()) }
                .distinct().associateWith { label ->
                    log2(modules.size.toDouble() / (modules.values.count { nodeList -> nodeList.any { node -> node.label.contains(label) } }))
                }
        }
        val moduleLabels = modules.mapValues { (thisCommunity, nodes) ->
            val tfidf = tf.getValue(thisCommunity).mapValues { (label, freq) ->
                freq * idf.getValue(thisCommunity).getValue(label)
            }
            tfidf.entries.sortedByDescending { it.value }
                .take(3)
                .joinToString("-") { it.key.lowercase() }
        }
        // Rank by conductance
        val modulesConductance = modules.mapValues { (thisCommunity, nodes) ->
            val edges = nodes.flatMap { node -> graph.getOutEdges(node) }
            val intraEdges = edges
                .count { it.target.getAttribute(Modularity.MODULARITY_CLASS) != thisCommunity }
            intraEdges.toDouble() / edges.count()
        }

// Preview

// Preview
        val previewModel = Lookup.getDefault().lookup(PreviewController::class.java).model
        previewModel.properties.putValue(PreviewProperty.SHOW_NODE_LABELS, true)
        previewModel.properties.putValue(PreviewProperty.ARROW_SIZE, 0.0)
        previewModel.properties.putValue(PreviewProperty.EDGE_RESCALE_WEIGHT, true)
        previewModel.properties.putValue(PreviewProperty.EDGE_CURVED, false)
        previewModel.properties.putValue(PreviewProperty.EDGE_RESCALE_WEIGHT_MIN, 1.0)
        previewModel.properties.putValue(PreviewProperty.EDGE_RESCALE_WEIGHT_MAX, 2.0)
        previewModel.properties.putValue(
            PreviewProperty.NODE_LABEL_FONT,
            previewModel.properties.getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(5)
        )

// Export

// Export
        val ec = Lookup.getDefault().lookup(ExportController::class.java)
        val exporter = ec.getExporter("gexf") as GraphExporter // Get GEXF exporter
        exporter.isExportVisible = true // Only exports the visible (filtered) graph
        exporter.workspace = workspace
        try {
            ec.exportFile(File(outputDir, "io_gexf.gexf"), exporter)
        } catch (ex: IOException) {
            ex.printStackTrace()
            return
        }

        // Export to Writer
        val exporterGraphML = ec.getExporter("graphml") // Get GraphML exporter
        exporterGraphML.workspace = workspace
        try {
            ec.exportFile(File(outputDir, "io_graphml.graphml"), exporterGraphML)
        } catch (ex: IOException) {
            ex.printStackTrace()
            return
        }

        val pagerank = graphModel.nodeTable.graph.nodes.associate { it.label to it.getAttribute(PageRank.PAGERANK) as Double }
            .filterValues { it > cap }
        exportHtml(ec, workspace, modulesConductance, moduleLabels, modules, pagerank, filterController, projFilter, graphModel)
    }

    private fun filterMonolithOrFeatures(value: String): Boolean =
        value == monolithModule || featureModules.map { it.toRegex() }.any { it.matches(value) }

    private fun exportHtml(
        ec: ExportController,
        workspace: Workspace?,
        modulesConductance: Map<Int, Double>,
        moduleLabels: Map<Int, String>,
        modules: Map<Int, List<Node>>,
        pageRank: Map<String, Double>,
        filterController: FilterController,
        projFilter: NodePartitionFilter,
        graphModel: GraphModel,
    ) {
        for (n in graphModel.graph.nodes) {
            val textProperties = n.textProperties
            val label = n.label ?: ""
            textProperties.text = label
            textProperties.setDimensions((label.length * 12).toFloat(), 30f)
            //System.out.println(textProperties.getWidth());
            //System.out.println(textProperties.getHeight());
            //System.out.println(textProperties.getText());
        }

        val wholeSvg = ec.renderSvg(workspace)
        File(outputDir, "whole_graph.svg").writeText(wholeSvg.toString())

        val modules = buildString {
            for ((idx, module) in modulesConductance.keys.sortedByDescending { modulesConductance[it] }
                .withIndex()) {
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
                    while (i < 25 && layout.canAlgo()) {
                        layout.goAlgo()
                        i += 1
                    }
                    layout.endAlgo()
                }
                LabelAdjust(null).apply {
                    setGraphModel(graphModel)
                    initAlgo()
                    var i = 0
                    while (i < 25 && canAlgo()) {
                        goAlgo()
                        i++
                    }
                    endAlgo()
                }
                // PNG Exporter config and export to Byte array
                appendLine(createHTML().h3 { +"${moduleLabels[module]}" })
                appendLine(createHTML().p { +"Conductance score: ${modulesConductance[module]}" })
                appendLine(ec.renderSvg(workspace))
                appendLine(
                    createHTML().details {
                        id = moduleLabels[module].toString()
                        open = true
                        summary("graph-container") {
                            +"Classes of this module"
                        }
                        modules[module]?.groupBy { it.getAttribute("module") }
                            ?.forEach { (module, nodes) ->
                                p { +"currently found in $module module:" }
                                ul {
                                    nodes.forEach {
                                        li { +(it.label ?: it.id.toString()) }
                                    }
                                }
                            }
                    }
                )
                appendLine(
                    createHTML().div {
                        val dependenciesCuts = modules[module]?.flatMap { node ->
                            graphModel.directedGraph.getOutEdges(node)
                                .filter { modules[module]?.contains(it.target) != true }
                                .filter { monolithModule == it.target.getAttribute("module") }
                                .map { edge -> node.label to edge.target.label }
                        }.orEmpty()
                        if (dependenciesCuts.isNotEmpty()) {
                            details {
                                summary("graph-container") {
                                    +"To extract this module you should break these dependencies:"
                                }
                                ul {
                                    dependenciesCuts.forEach { (src, trg) ->
                                        li { +"$src -> $trg" }
                                    }
                                }
                            }
                        } else {
                            p { +"To extract this module you don't need to break any dependencies in $monolithModule" }
                        }
                    }
                )
                filterController.remove(query)
                filterController.remove(query2)
            }
        }

        val nodesPageRank = createHTML().ul {
            pageRank.entries.sortedBy { it.value }.forEach { pageranked -> li { +"${pageranked.key}: ${pageranked.value}" } }
        }

        val modulesTable = createHTML().table {
            thead {
                tr {
                    th { +"Module" }
                    th { +"Conductance" }
                }
            }
            tbody {
                modulesConductance.entries.sortedByDescending { it.value }.forEach { (module, conductance) ->
                    tr {
                        td { +moduleLabels[module].toString() }
                        td { +conductance.toString() }
                    }
                }
            }
        }

        val template = javaClass.getResource("/template.html").readText()
            .replace("@@whole graph@@", wholeSvg.toString())
            .replace("@@monolith_modules@@", modules)
            .replace("@@monolith_modules_table@@", modulesTable)
            .replace("@@cores@@", nodesPageRank)

        File(outputDir, "report.html").writeText(template)
    }

    private fun ExportController.renderSvg(workspace: Workspace?): String? {
        val svgExporter = getExporter("svg") as SVGExporter
        svgExporter.workspace = workspace
        val wholeSvg =
            try {
                val writer = StringWriter()
                exportWriter(writer, svgExporter)
                writer
            } catch (ex: IOException) {
                ex.printStackTrace()
                null
            }
        return wholeSvg?.toString()?.replace("<svg", "<svg class=\"graph\"")
    }

    private fun ImportController.importContainer(file: File): Container? {
        val container: Container
        try {
            container = importFile(file)
            container.loader.setEdgeDefault(EdgeDirectionDefault.DIRECTED) // Force DIRECTED
            container.loader.setAllowAutoNode(false) // Don't create missing nodes
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
