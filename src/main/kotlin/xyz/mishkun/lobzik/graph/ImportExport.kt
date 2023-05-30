package xyz.mishkun.lobzik.graph

import org.gephi.io.exporter.api.ExportController
import org.gephi.io.exporter.preview.PDFExporter
import org.gephi.io.exporter.spi.CharacterExporter
import org.gephi.io.exporter.spi.GraphExporter
import org.gephi.io.importer.api.Container
import org.gephi.io.importer.api.EdgeDirectionDefault
import org.gephi.io.importer.api.ImportController
import org.gephi.io.processor.plugin.DefaultProcessor
import org.gephi.project.api.ProjectController
import org.openide.util.Lookup
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.StringWriter


/**
 * This demo focuses on Import and Export features, showing different IO
 * possibilities.
 *
 *
 * Import can be performed from a file, database or Reader/InputStream. The
 * export can be done to files and Writer/OutputStream. The demo import a file
 * and shows how to configure graph export to use the visible graph instead of
 * the full graph. That is essential to export a graph that has been filtered.
 *
 *
 * The ability to export graph or PDF to Writer or Byte array is showed at the
 * end.
 *
 * @author Mathieu Bastian
 */
class ImportExport {
    fun script() {
        //Init a project - and therefore a workspace
        val pc = Lookup.getDefault().lookup(ProjectController::class.java)
        pc.newProject()
        val workspace = pc.currentWorkspace

        //Get controllers and models
        val importController = Lookup.getDefault().lookup(
            ImportController::class.java
        )

        //Import file
        val container: Container
        try {
            val file = File(javaClass.getResource("/org/gephi/toolkit/demos/lesmiserables.gml").toURI())
            container = importController.importFile(file)
            container.loader.setEdgeDefault(EdgeDirectionDefault.DIRECTED) //Force DIRECTED
            container.loader.setAllowAutoNode(false) //Don't create missing nodes
        } catch (ex: Exception) {
            ex.printStackTrace()
            return
        }

        //Append imported data to GraphAPI
        importController.process(container, DefaultProcessor(), workspace)

        //Export full graph
        val ec = Lookup.getDefault().lookup(ExportController::class.java)
        try {
            ec.exportFile(File("io_gexf.gexf"))
        } catch (ex: IOException) {
            ex.printStackTrace()
            return
        }

        //Export only visible graph
        val exporter = ec.getExporter("gexf") as GraphExporter //Get GEXF exporter
        exporter.isExportVisible = true //Only exports the visible (filtered) graph
        exporter.workspace = workspace
        try {
            ec.exportFile(File("io_gexf.gexf"), exporter)
        } catch (ex: IOException) {
            ex.printStackTrace()
            return
        }

        //Export to Writer
        val exporterGraphML = ec.getExporter("graphml") //Get GraphML exporter
        exporterGraphML.workspace = workspace
        val stringWriter = StringWriter()
        ec.exportWriter(stringWriter, exporterGraphML as CharacterExporter)
        //System.out.println(stringWriter.toString());   //Uncomment this line

        //PDF Exporter config and export to Byte array
        val pdfExporter = ec.getExporter("pdf") as PDFExporter
        pdfExporter.workspace = workspace
        val baos = ByteArrayOutputStream()
        ec.exportStream(baos, pdfExporter)
        val pdf = baos.toByteArray()
    }
}
