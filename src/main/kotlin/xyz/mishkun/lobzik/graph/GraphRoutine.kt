/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.mishkun.lobzik.graph

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.gephi.appearance.api.AppearanceController
import org.gephi.appearance.api.PartitionFunction
import org.gephi.appearance.plugin.PartitionElementColorTransformer
import org.gephi.appearance.plugin.palette.PaletteManager
import org.gephi.filters.api.FilterController
import org.gephi.filters.api.Query
import org.gephi.filters.plugin.AbstractFilter
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
import org.gephi.statistics.plugin.Hits
import org.gephi.statistics.plugin.Modularity
import org.openide.util.Lookup
import space.kscience.plotly.*
import space.kscience.plotly.models.ScatterMode
import java.awt.Color
import java.io.File
import java.io.IOException
import java.io.StringWriter
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.random.Random


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

        val projectColumn = graphModel.nodeTable.getColumn("module")
        val projFunc = appearanceModel.getNodeFunction(projectColumn, PartitionElementColorTransformer::class.java)
        val projPartition = (projFunc as PartitionFunction).partition
        val projFilter = NodePartitionFilter(appearanceModel, projPartition)
        val selected = projFilter.partition.getValues(graph).map { it.toString() }.filter(::filterMonolithOrFeatures)
        projFilter.parts = selected.toSet()
        projFilter.init(graph)
        val partitionProjectQuery: Query = filterController.createQuery(projFilter)
        filterController.setSubQuery(giantComponentQuery, partitionProjectQuery)

        graphModel.visibleView = filterController.filter(giantComponentQuery)

        val degree = Degree()
        degree.execute(graphModel)
        val hits = Hits()
        hits.execute(graphModel)

        graphModel.visibleView = filterController.filter(giantComponentQuery)

// See visible graph stats

        filterController.exportToColumn("isModularized", giantComponentQuery)
// See visible graph stats
        val graphVisible = graphModel.undirectedGraphVisible
        println("Filtered Nodes: " + graphVisible.nodeCount)
        println("Filtered Edges: " + graphVisible.edgeCount)

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

// Rank color by Modularity Class
        val modColumn = graphModel.nodeTable.getColumn(Modularity.MODULARITY_CLASS)
        val func = appearanceModel.getNodeFunction(modColumn, PartitionElementColorTransformer::class.java)
        val partition = (func as PartitionFunction).partition
        println("Found ${partition.size(graph)} partitions")
        val palette = PaletteManager.getInstance().randomPalette(partition.size(graph))
        partition.setColors(graph, palette.colors)
        appearanceController.transform(func)

        // extract module nodes from graph model
        val modules =
            graphModel.directedGraphVisible.nodes.groupBy { it.getAttribute(Modularity.MODULARITY_CLASS) as Int }
        // Execute tf-idf on module labels treating modules as documents
        val tf = modules.mapValues { (_, nodes) ->
            nodes.flatMap { it.label.split("(?<=.)(?=\\p{Upper})".toRegex()) }
                .fold(mutableMapOf<String, Int>()) { acc, el -> acc.merge(el, 1, Int::plus); acc }
        }
        val idf = modules.mapValues { (_, nodes) ->
            nodes.flatMap { it.label.split("(?<=.)(?=\\p{Upper})".toRegex()) }
                .distinct().associateWith { label ->
                    log2(modules.size.toDouble() / (modules.values.count { nodeList ->
                        nodeList.any { node ->
                            node.label.contains(
                                label
                            )
                        }
                    }))
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

// Preview

// Preview
        val previewModel = Lookup.getDefault().lookup(PreviewController::class.java).model
        previewModel.properties.putValue(PreviewProperty.SHOW_NODE_LABELS, true)
        previewModel.properties.putValue(PreviewProperty.ARROW_SIZE, 1.0)
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

        exportHtml(
            ec,
            workspace,
            moduleLabels,
            modules,
            filterController,
            projFilter,
            graphModel
        )
    }

    private fun filterMonolithOrFeatures(value: String): Boolean =
        value == monolithModule || featureModules.map { it.toRegex() }.any { it.matches(value) }

    private fun exportHtml(
        ec: ExportController,
        workspace: Workspace?,
        moduleLabels: Map<Int, String>,
        modules: Map<Int, List<Node>>,
        filterController: FilterController,
        projFilter: NodePartitionFilter,
        graphModel: GraphModel,
    ) {
        val modulesConductance = modules.mapValues { (thisCommunity, nodes) ->
            val edges = nodes.flatMap { node -> graphModel.directedGraph.getOutEdges(node) }
            val intraEdges = edges
                .filter { it.target.getAttribute("module")?.let(Any::toString)?.let(::filterMonolithOrFeatures) == true }
                .filter { it.target.getAttribute(Modularity.MODULARITY_CLASS) != thisCommunity }
                .sumOf { it.weight }
            intraEdges / edges.sumOf { it.weight }
        }
        val modulesCut = modules.mapValues { (thisCommunity, nodes) ->
            val edges = nodes.flatMap { node -> graphModel.directedGraph.getOutEdges(node) }
            val intraEdges = edges
                .filter { it.target.getAttribute("module")?.let(Any::toString)?.let(::filterMonolithOrFeatures) == true }
                .filter { it.target.getAttribute(Modularity.MODULARITY_CLASS) != thisCommunity }
                .sumOf { it.weight }
            intraEdges
        }
        val modulesMonolithCut = modules.mapValues { (thisCommunity, nodes) ->
            val edges = nodes.flatMap { node -> graphModel.directedGraph.getOutEdges(node) }
            val intraEdges = edges
                .filter { it.target.getAttribute(Modularity.MODULARITY_CLASS) != thisCommunity }
                .filter { it.target.getAttribute("module") == monolithModule }
                .sumOf { it.weight }
            intraEdges
        }
        for (n in graphModel.graph.nodes) {
            val textProperties = n.textProperties
            val label = n.label ?: ""
            textProperties.text = label
            textProperties.setDimensions((label.length * 12).toFloat(), 30f)
        }

        val monolithNodes = graphModel.graph.nodes.filter { it.getAttribute("module") == monolithModule }

        val monolithModulesRendered = buildString {
            for ((idx, module) in modulesConductance.keys.sortedBy { modulesConductance[it] }
                .withIndex()) {
                if (modules[module].orEmpty().none { it in monolithNodes }) {
                    continue
                }
                println("Started exporting ${moduleLabels[module]} [$idx/${modules.keys.size}]")
                val nodeSet = modules[module].orEmpty().toSet()
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
                        open = false
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

        val nodesAuth = createHTML().details {
            summary("graph-container") { +"Class outliers rated by Authority" }
            div {
                table("sortable") {
                    thead {
                        tr {
                            th { +"Class" }
                            th { +"InDegree" }
                            th { +"OutDegree" }
                            th { +"Authority" }
                            th { +"Hub" }
                        }
                    }
                    tbody {
                        val cap = graphModel.nodeIndex.values(graphModel.nodeTable.getColumn(Hits.AUTHORITY))
                            .map { it as Float }.sorted()
                            .run { elementAt(ceil(size * 0.95).toInt()) }
                        val hubCap =
                            graphModel.nodeIndex.values(graphModel.nodeTable.getColumn(Hits.HUB)).map { it as Float }
                                .sorted()
                                .run { elementAt(ceil(size * 0.95).toInt()) }
                        val degreeCap =
                            graphModel.graph.nodes.map { it.getAttribute(graphModel.nodeTable.getColumn(Degree.DEGREE)) }
                                .map { it as Int }.sorted()
                                .run { elementAt(ceil(size * 0.95).toInt()) }

                        graphModel.nodeTable.graph.nodes.forEach { node ->
                            val inDegree = node.getAttribute(graphModel.nodeTable.getColumn(Degree.INDEGREE)) as Int
                            val outDegree = node.getAttribute(graphModel.nodeTable.getColumn(Degree.OUTDEGREE)) as Int
                            val authority = node.getAttribute(graphModel.nodeTable.getColumn(Hits.AUTHORITY)) as Float
                            val hub = node.getAttribute(graphModel.nodeTable.getColumn(Hits.HUB)) as Float
                            if (authority >= cap || hub >= hubCap || inDegree >= degreeCap || outDegree >= degreeCap) {
                                tr {
                                    td { +node.label.toString() }
                                    td { +inDegree.toString() }
                                    td { +outDegree.toString() }
                                    td { +authority.toString() }
                                    td { +hub.toString() }
                                }
                            }
                        }
                    }
                }
            }
        }

        val modulesTable = createHTML().div {
            table("sortable") {
                thead {
                    tr {
                        th { +"Module" }
                        th { +"Conductance" }
                        th { +"Cut" }
                        th { +"MonolithCut" }
                        th { +"MonolithClasses" }
                        th { +"NonMonolithClasses" }
                    }
                }
                tbody {
                    modulesConductance.entries.sortedBy { it.value }.forEach { (module, conductance) ->
                        tr {
                            td {
                                val moduleName = moduleLabels[module].toString()
                                a {
                                    href = "#$moduleName"
                                    +moduleName
                                }
                            }
                            td { +conductance.toString() }
                            td { +modulesCut[module].toString() }
                            td { +modulesMonolithCut[module].toString() }
                            td { +modules[module].orEmpty().count { it in monolithNodes }.toString() }
                            td { +modules[module].orEmpty().count { it !in monolithNodes }.toString() }
                        }
                    }
                }
            }
            div {
                Plotly.plot {
                    scatter {
                        val values = modulesConductance.entries.sortedBy { it.value }
                        y.set(values.map { it.value }.toList())
                        x.set(values.map { moduleLabels[it.key].toString() }.toList())
                        mode = ScatterMode.markers
                        marker {
                            size = 16
                        }
                    }
                    layout {
                        title = "Modules sorted by conductance"
                        xaxis {
                            title = "Module"
                            showticklabels = false
                        }
                        yaxis {
                            title = "Conductance"
                        }
                    }
                }.let { plot ->
                    id = "plot"
                    unsafe { raw(plot.toHTML()) }
                }
            }
        }

        val modulesSvg = modulesGraph(modules, moduleLabels, modulesConductance, graphModel)

        val template = javaClass.getResource("/template.html").readText()
            .replace("@@whole graph@@", modulesSvg.toString())
            .replace("@@monolith_modules@@", monolithModulesRendered)
            .replace("@@monolith_modules_table@@", modulesTable)
            .replace("@@cores@@", nodesAuth)

        File(outputDir, "report.html").writeText(template)
    }

    private fun modulesGraph(
        modules: Map<Int, List<Node>>,
        labels: Map<Int, String>,
        modulesConductance: Map<Int, Double>,
        oldGraph: GraphModel
    ): String? {
        val projectColumn = oldGraph.nodeTable.getColumn("module")
        val moduleMap = modules.mapValues { (module, nodes) ->
            nodes.flatMap { node ->
                oldGraph.directedGraph.getOutEdges(node)
                    .mapNotNull { edge ->
                        (edge.target.getAttribute(Modularity.MODULARITY_CLASS) as? Int)?.takeUnless { it == module || it == 0 }
                            ?.takeIf { edge.target.getAttribute(projectColumn).toString() == monolithModule }
                    }
            }
        }

        val pc = Lookup.getDefault().lookup(ProjectController::class.java)
        pc.openNewWorkspace()
        val graphModel: GraphModel = Lookup.getDefault().lookup(GraphController::class.java).graphModel
        for ((module, _) in modules) {
            val newNode = graphModel.factory().newNode(module.toString())
            newNode.label = "${labels[module]};${String.format("%.3f", modulesConductance[module])}"
            newNode.setSize(10f)
            newNode.color = generateRandomColor(Color.getColor("#e1a5fa"))
            graphModel.graph.addNode(newNode)
        }
        moduleMap.forEach { (src, dependencies) ->
            dependencies.fold(HashMap<Int, Int>()) { acc, i -> acc.merge(i, 1, Int::plus); acc }
                .forEach { (trg, weight) ->
                    val newEdge = graphModel.factory().newEdge(
                        graphModel.graph.getNode(src.toString()),
                        graphModel.graph.getNode(trg.toString()),
                        weight,
                        true,
                    )
                    newEdge.label = weight.toString()
                    graphModel.graph.addEdge(newEdge)
            }
        }

        ForceAtlas2(null).also { layout ->
            layout.setGraphModel(graphModel)
            layout.resetPropertiesValues()
            layout.isNormalizeEdgeWeights = true
            layout.edgeWeightInfluence = 10.0
            layout.scalingRatio = 100.0
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
            var i = 0
            while (i < 25 && canAlgo()) {
                goAlgo()
                i++
            }
            endAlgo()
        }

        val previewModel = Lookup.getDefault().lookup(PreviewController::class.java).model
        previewModel.properties.putValue(PreviewProperty.SHOW_NODE_LABELS, true)
        previewModel.properties.putValue(PreviewProperty.EDGE_CURVED, false)
        previewModel.properties.putValue(PreviewProperty.SHOW_EDGE_LABELS, true)
        previewModel.properties.putValue(
            PreviewProperty.EDGE_LABEL_FONT,
            previewModel.properties.getFontValue(PreviewProperty.EDGE_LABEL_FONT).deriveFont(10)
        )
        previewModel.properties.putValue(
            PreviewProperty.NODE_LABEL_FONT,
            previewModel.properties.getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(10)
        )

// Filter Giant Component
        val filterController = Lookup.getDefault().lookup(FilterController::class.java)
        val giantComponentFilter = GiantComponentBuilder.GiantComponentFilter()
        giantComponentFilter.init(graphModel.graph)
        val giantComponentQuery = filterController.createQuery(giantComponentFilter)

        graphModel.visibleView = filterController.filter(giantComponentQuery)

        val ec = Lookup.getDefault().lookup(ExportController::class.java)

        val exporterGraphML = ec.getExporter("graphml") // Get GraphML exporter
        exporterGraphML.workspace = pc.currentWorkspace
        try {
            ec.exportFile(File(outputDir, "modulesGraph.graphml"), exporterGraphML)
        } catch (ex: IOException) {
            ex.printStackTrace()
        }

        return ec.renderSvg(pc.currentWorkspace)
    }

    private fun generateRandomColor(mix: Color?): Color? {
        var red: Int = Random.nextInt(256)
        var green: Int = Random.nextInt(256)
        var blue: Int = Random.nextInt(256)

        // mix the color
        if (mix != null) {
            red = (red + mix.red) / 2
            green = (green + mix.green) / 2
            blue = (blue + mix.blue) / 2
        }
        return Color(red, green, blue)
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
            return graph.nodeCount != 0
        }

        override fun evaluate(graph: Graph, node: Node): Boolean {
            return node in nodes || node in neighbourhood
        }

        override fun finish() {
        }
    }
}
