package deltageneradorfacturas;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRExporter;
import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRPdfExporter;

//import rponte.report.ConnectionFactory;

/**
 * You'll need these jar's below:
 *	
 * jasperreports-5.0.1.jar
 * iText-2.1.7.jar (needed to generate PDF)
 * jfreechart-1.0.12.jar (needed to graphics and charts)
 * jcommon-1.0.15.jar (needed to graphics and charts)
 * commons-beanutils-1.8.2.jar
 * commons-collections-3.2.1.jar
 * commons-digester-2.1.jar
 * commons-logging-1.1.jar
 */
public class GenerateSimplePdfReportWithJasperReports {
        static private Properties PROPS;
        static private String PATH_DOCUMENTOS="";
        //static private String PATH_WWW=""; 
        static private String DIR_REPORTES=""; 
        static private String REPORTE=""; 
        static private String JASPER="";
        

        
        
	public static void generaPDF(Connection conn, java.math.BigDecimal nrointerno, String pdfname) {
		
		Connection connection = null;
		try {
                        PROPS = new Properties();
                        PROPS.load(GenerateSimplePdfReportWithJasperReports.class.getResourceAsStream("system.properties"));
                        
        
                         PATH_DOCUMENTOS = PROPS.getProperty("path.documentos").trim();
                        // PATH_WWW = PROPS.getProperty("path.wwww").trim(); 
                         DIR_REPORTES = PROPS.getProperty("path.dirreportes").trim(); 
                         REPORTE = PROPS.getProperty("path.reporte").trim(); 
                         JASPER = PROPS.getProperty("path.jasper").trim(); 
 
                         
                         
                         
		        String idempresa = "1";
			String reportName = DIR_REPORTES;
			Map<String, Object> parameters = new HashMap<String, Object>();
                        
                        if (!parameters.containsKey("subreport_dir"))
                           parameters.put("subreport_dir", DIR_REPORTES);
                        parameters.put("nrointerno", nrointerno);
                        
                        //System.setProperty("jasper.reports.compile.class.path","/datos/desarrollo/jboss-5.1.0.GA/server/default/custom/reportes/" );
                        
                        System.setProperty("jasper.reports.compile.temp",DIR_REPORTES );
                        // por lo visto no va
                        //parameters.put("idempresa", idempresa);
                        
			//connection = new ConnectionFactory().getConnection(); // opens a jdbc connection
                        //Conexion c = new Conexion();
                 
                        connection = conn;
			// compiles jrxml
			JasperCompileManager.compileReportToFile(reportName + REPORTE );
                        
			// fills compiled report with parameters and a connection
                        
			JasperPrint print = JasperFillManager.fillReport(reportName + JASPER, parameters, connection);
			// exports report to pdf
			JRExporter exporter = new JRPdfExporter();
			exporter.setParameter(JRExporterParameter.JASPER_PRINT, print);
			exporter.setParameter(JRExporterParameter.OUTPUT_STREAM, new FileOutputStream(PATH_DOCUMENTOS + pdfname)); // your output goes here
			
			exporter.exportReport();

		} catch (Exception e) {
                        System.out.println("Vuelco: "+ e);
			throw new RuntimeException("It's not possible to generate the pdf report.", e);
		} finally {
			// it's your responsibility to close the connection, don't forget it!
			if (connection != null) {
				//try { connection.close(); } catch (Exception e) {}
			}
		}
		
	}
}