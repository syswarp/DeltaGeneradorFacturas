/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package deltageneradorfacturas;

import static deltageneradorfacturas.DeltaGeneradorFacturas.CONEXION_LOCAL;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Properties;
import java.util.Vector;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

public class DeltaGeneradorFacturas {

    static Connection CONEXION_LOCAL;
    static String USERNAME = "";
    static String PASSWORD = "";
    static String CC = "";
    static String BCC = "";
    static String PATH_DOCUMENTOS = "";
    static String PATH_WWW = "";
    
    

    
    
    private static Properties PROPS;
    static Logger log = Logger.getLogger(DeltaGeneradorFacturas.class);
 
    public DeltaGeneradorFacturas() {
        super();

    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    
        BasicConfigurator.configure();
        Vector<String> v = new Vector<>();
        try {
            PROPS = new Properties();
            PROPS.load(DeltaGeneradorFacturas.class.getResourceAsStream("system.properties"));
            
        
            Conexion con = new Conexion();
            con.setConexionLocal();

           if (con.isConexionLocal()) {

                CONEXION_LOCAL = con.getConexionLocal();
            }
            PATH_DOCUMENTOS = PROPS.getProperty("path.documentos").trim();
            PATH_WWW = PROPS.getProperty("path.www").trim();
            ResultSet rsPendientes = getFacturasPendientes(CONEXION_LOCAL);
            BigDecimal nrointerno = new BigDecimal(0);
            BigDecimal idcliente = new BigDecimal(0);            
            
            while (rsPendientes.next()) {
                nrointerno = rsPendientes.getBigDecimal("nrointerno");
                idcliente = rsPendientes.getBigDecimal("idcliente");
                // reviso si se tengo que hacer mas de un attach.
                String attachMail2 = null;
                
                
                System.out.println("Numero interno: " + nrointerno);
                String pdf_name = rsPendientes.getString("tipomovs") +"-"+rsPendientes.getString("documento")+".pdf" ;
                GenerateSimplePdfReportWithJasperReports.generaPDF(CONEXION_LOCAL, nrointerno, pdf_name);
                String asuntoMail = "BELL S.A. - Envio de documento comercial electronico Nro: "+rsPendientes.getString("tipomovs") +"-"+rsPendientes.getString("documento");
                String bodyMail   = "Estimado Cliente: \n Por medio de la presente le hacemos llegar adjunto el documento electronico Nro:" +rsPendientes.getString("tipomovs") +"-"+rsPendientes.getString("documento") + "\n";
                //bodyMail += "Tambien puede acceder al documento haciendo click aqui: " + PATH_WWW  +rsPendientes.getString("tipomovs") +"-"+rsPendientes.getString("documento") +".pdf" ;
                bodyMail +="\n";
                bodyMail +="\n";
                bodyMail +="\n";
                bodyMail +="BELL S.A.\n";
                bodyMail +="Cuenta Corriente en pesos Nro: 015-010405-0\n";
                bodyMail +="CBU.: 19100155 55001 501040500\n";
                bodyMail +="BANCO CREDICOOP\n";
                bodyMail +="\n";
                bodyMail +="\n";
                bodyMail +="\n";
                
                String attachMail = PATH_DOCUMENTOS + rsPendientes.getString("tipomovs") +"-"+rsPendientes.getString("documento")+".pdf";
                String llevaMSDS = isAdjuntaMSDS(CONEXION_LOCAL,  idcliente);
                if (llevaMSDS.equalsIgnoreCase("S")){
                    ResultSet rsArticulos = getArticulos(CONEXION_LOCAL,  nrointerno);
                    
                    while(rsArticulos.next()){
                        String msds = PATH_DOCUMENTOS +"MSDS_"+rsArticulos.getString("codigo_st")+".pdf";
                        v.add(msds);
                    }
                    
                    
                    // todo: definir como hago para mandar un vector de attanchment o algo asi.
                    
                    
                   //attachMail2= PATH_DOCUMENTOS +"MSDS.pdf";
                }
                
                String email = rsPendientes.getString("email");
                sendMail( email, asuntoMail, bodyMail, attachMail,  v);
                v=new Vector<>();
                //.. finalmente actualizo registro
                facturasPendientesUPD(CONEXION_LOCAL, nrointerno);

            }
        } catch (Exception ex) {
            log.error("Problemas con el archivo system.properties, por favor revisar. " + ex);
        }
    }
    

    private static ResultSet getFacturasPendientes(Connection conn) {
        ResultSet salida = null;
        try {           
          
            String cQuery = " select mc.nrointerno, mc.sucursal, mc.comprob, mc.tipomovs, to_comprobante(mc.sucursal, mc.comprob) as documento, mail.email, mc.cliente as idcliente  from    clientesmovcli mc join clientesemail mail on ( mc.cliente = mail.idcliente and mc.idempresa = mail.idempresa )  \n"
                    + " where mc.sucursal = 5  \n"
                    + " and mc.afipcae is not null and mc.gendoc is null order by nrointerno; \n";
            Statement statement = conn.createStatement();
            salida = statement.executeQuery(cQuery);
        } catch (Exception ex) {
            System.out.println("getFacturasPendientes / Error: " + ex);
        }
        return salida;
    }

      private static ResultSet getArticulos(Connection conn, BigDecimal nrointerno) {
        ResultSet salida = null;
        try {           
          
            String cQuery = " SELECT upper(trim(replace(st.codigo_st,' ',''))) as codigo_st "
                    + " FROM pedidos_cabe pc "
                    + " INNER JOIN pedidos_deta pd ON pc.idpedido_cabe = pd.idpedido_cabe AND pc.idempresa = pd.idempresa "
                    + " INNER JOIN stockmedidas sm ON pd.codigo_md = sm.codigo_md AND pd.idempresa = sm.idempresa "
                    + " INNER JOIN pedidosrotuloslote prl ON pd.idpedido_deta = prl.idpedido_deta AND pd.idempresa = prl.idempresa "
                    + " INNER JOIN pedidosprioridades pp ON pc.idprioridad = pp.idprioridad AND pc.idempresa = pp.idempresa "                  
                    + " INNER JOIN clientesremitos cr ON prl.idremitocliente = cr.idremitocliente AND prl.idempresa = cr.idempresa "
                    + " INNER JOIN stockstock st ON pd.codigo_st = st.codigo_st  AND pd.idempresa = st.idempresa "
                    + " AND st.inventa_st = 'S' "
                    + " INNER JOIN clientesmovcli mc ON cr.nrointerno_mc = mc.nrointerno AND cr.idempresa = mc.idempresa "
                    + " INNER JOIN globalmonedas gm ON mc.moneda = gm.idmoneda "
                    + " WHERE mc.nrointerno =" + nrointerno
                    + " AND mc.idempresa = '1'::numeric "
                    + "  ";
            
            
            Statement statement = conn.createStatement();
            salida = statement.executeQuery(cQuery);
        } catch (Exception ex) {
            System.out.println("getFacturasPendientes / Error: " + ex);
        }
        return salida;
    }
  
    

    
    
     private static String isAdjuntaMSDS(Connection conn, BigDecimal idcliente) {
        ResultSet rssalida = null;
        String salida = "N";
        try {           
          
            String cQuery = " select case count(*) when 0 then 'N' else 'S' end as envia_msds from clientesclientes where observacion ilike 'msds' and idcliente =" + idcliente.toString();
             Statement statement = conn.createStatement();
            rssalida = statement.executeQuery(cQuery);
            while(rssalida.next()){
              salida = rssalida.getString("envia_msds");
            }
        } catch (Exception ex) {
            System.out.println("isAdjuntaMSDS / Error: " + ex);
        }
        return salida;
    }
   
    
    
    
    
    
    public static int facturasPendientesUPD(Connection conn, java.math.BigDecimal nrointerno) {
        String cQuery = "update clientesmovcli set gendoc='S', sentmail='S' where nrointerno=" + nrointerno.toString();
        int i = 0;
        try {
            Statement statement = conn.createStatement();
            statement.execute(cQuery);
        } catch (SQLException sqlException) {
            System.out.println("Error SQL: " + sqlException);
        } catch (Exception ex) {
            System.out.println("Salida por exception: " + ex);
        }
        return i;
    }

    
    public static String  sendMail( String toMail, String asuntoMail, String bodyMail, String attachMail, Vector v){
      String salida = "NOOK";
      try{
            PROPS = new Properties();
            PROPS.load(DeltaGeneradorFacturas.class.getResourceAsStream("system.properties"));
            
            USERNAME = PROPS.getProperty("mail.username").trim();
            PASSWORD = PROPS.getProperty("mail.password").trim();
            CC = PROPS.getProperty("mail.cc").trim();
            BCC = PROPS.getProperty("mail.bcc").trim();

      } catch (Exception e) {
         throw new RuntimeException(e);
      }       
            

      // Sender's email ID needs to be mentioned
      String from = USERNAME;

      final String username = USERNAME;
      final String password = PASSWORD;

      // Assuming you are sending email through relay.jangosmtp.net
    //  String host = "mail.syswarp.com.ar";
     String host = "mail.bellfragancias.com";
    // String host = "mail.smtp2go.com";
      
      
      Properties props = new Properties();
      props.put("mail.smtp.auth", "true");
      props.put("mail.smtp.starttls.enable", "true");
      props.put("mail.smtp.host", host);
      props.put("mail.smtp.port", "465");
      props.put("mail.smtp.socketFactory.class", 
                "javax.net.ssl.SSLSocketFactory"); 
      
    //  props.put("mail.smtp.port", "2525");
      
        // Get the Session object.
      Session session = Session.getInstance(props,
         new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
               return new PasswordAuthentication(username, password);
            }
         });

     try {
         // Create a default MimeMessage object.
         Message message = new MimeMessage(session);

         // Set From: header field of the header.
         message.setFrom(new InternetAddress(from));

         // Set To: header field of the header.
         message.setRecipients(Message.RecipientType.TO,
            InternetAddress.parse(toMail));
         
          message.setRecipients(Message.RecipientType.CC,
            InternetAddress.parse(CC));

          message.setRecipients(Message.RecipientType.BCC,
            InternetAddress.parse(BCC));

          
         // Set Subject: header field
         message.setSubject(asuntoMail);

         // Create the message part
         BodyPart messageBodyPart = new MimeBodyPart();

         // Now set the actual message
         messageBodyPart.setText(bodyMail);

         // Create a multipar message
         Multipart multipart = new MimeMultipart();

         // Set text message part
         multipart.addBodyPart(messageBodyPart);

         // Part two is attachment
         messageBodyPart = new MimeBodyPart();
         String filename = attachMail;
         DataSource source = new FileDataSource(filename);
         messageBodyPart.setDataHandler(new DataHandler(source));
         messageBodyPart.setFileName(filename);
         multipart.addBodyPart(messageBodyPart);

 // segundo attach
         
         for (int i = 0; i < v.size(); i++) {
         
             messageBodyPart = new MimeBodyPart();
             String nombre = v.get(i).toString();
             filename = nombre;
             source = new FileDataSource(filename);
             messageBodyPart.setDataHandler(new DataHandler(source));
             messageBodyPart.setFileName(filename);
             multipart.addBodyPart(messageBodyPart); 
             
             
             
         }
         
         
         // Send the complete message parts
         message.setContent(multipart);

         // Send message
         Transport.send(message);

         System.out.println("Se ha enviado el email a la cuenta: "+toMail+" en forma correcta....");
         salida = "OK";
      } catch (MessagingException e) {
         throw new RuntimeException(e);
      }       
      return salida;
    }
    
    
    
    
}
