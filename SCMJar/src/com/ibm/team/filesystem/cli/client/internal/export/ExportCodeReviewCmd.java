package com.ibm.team.filesystem.cli.client.internal.export;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.common.workitems.internal.rest.IFilesystemWorkItemRestService;
import com.ibm.team.filesystem.common.workitems.internal.rest.IFilesystemWorkItemRestService.ParmsGetCodeReviewReport;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.repository.common.transport.HttpUtil.CharsetEncoding;
import com.ibm.team.repository.common.transport.HttpUtil.MediaType;
import com.ibm.team.repository.transport.client.ITeamRawRestServiceClient;
import com.ibm.team.repository.transport.client.ITeamRawRestServiceClient.IRawRestClientConnection;
import com.ibm.team.repository.transport.client.ITeamRawRestServiceClient.IRawRestClientConnection.Response;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.client.internal.WorkspaceManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.eclipse.osgi.util.NLS;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;








public class ExportCodeReviewCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  private static final String QUERY_ID = "queryId";
  private static final String NEXT_PAGE_TOKEN = "nextPageToken";
  private static final String INCLUDE_CS_DELIVERY_INFO = "includeCsDeliveryInfo";
  private static final String CODE_REVIEW_REPORT = "codeReviewReport";
  private static final String RESPONSE = "response";
  private static final String RESPONSE_RETURN_FINAL = "returnValue";
  private static final String RESPONSE_VALUE = "value";
  private static final String RESPONSE_CODE_REVIEW_EXPORT = "codeReviewExport";
  private static final String RESPONSE_CODE_REVIEWS = "codeReviews";
  private static final String RESPONSE_WORK_ITEM_QUERY = "workItemQuery";
  private static final String RESPONSE_SOAPENV_BODY = "soapenv:Body";
  private static final String RESPONE_EQUALIFIED_CLASS_NAME = "_eQualifiedClassName";
  
  public ExportCodeReviewCmd() {}
  
  private static final PositionalOptionDefinition OPT_QUERY_ID = new PositionalOptionDefinition("queryId", 1, 1);
  private static final PositionalOptionDefinition OPT_DEST = new PositionalOptionDefinition("outputFile", 1, 1);
  private static final NamedOptionDefinition OPT_INCLUDE_CS_INFO = new NamedOptionDefinition("c", "includeCsDeliveryInfo", 0);
  private static final NamedOptionDefinition OPT_FORMAT_XML = new NamedOptionDefinition("x", "xml", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false);
    SubcommandUtil.addRepoLocationToOptions(options, true, true);
    options.setLongHelp(Messages.ExportCodeReviewCmd_0);
    options.addOption(OPT_QUERY_ID, Messages.ExportCodeReviewCmd_1)
      .addOption(OPT_DEST, Messages.ExportCodeReviewCmd_2)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT, false)
      .addOption(OPT_FORMAT_XML, Messages.ExportCodeReviewCmd_3, false))
      .addOption(OPT_INCLUDE_CS_INFO, Messages.ExportCodeReviewCmd_4);
    return options;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    String outputLocation = cli.getOption(OPT_DEST);
    File file = new File(outputLocation);
    if (file.exists()) {
      throw StatusHelper.inappropriateArgument(Messages.ExportCodeReviewCmd_5);
    }
    
    ITeamRepository repo = RepoUtil.login(config, config.getConnectionInfo());
    IClientLibraryContext context = ((WorkspaceManager)SCMPlatform.getWorkspaceManager(repo)).getContext();
    
    IFilesystemWorkItemRestService.ParmsGetCodeReviewReport parms = new IFilesystemWorkItemRestService.ParmsGetCodeReviewReport();
    queryId = cli.getOption(OPT_QUERY_ID);
    nextPageToken = "0";
    includeCsDeliveryInfo = cli.hasOption(OPT_INCLUDE_CS_INFO);
    
    boolean isXml = cli.hasOption(OPT_FORMAT_XML);
    if ((isXml) && (cli.hasOption(CommonOptions.OPT_JSON))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ExportCodeReviewCmd_6, 
        OPT_FORMAT_XML.getName(), 
        CommonOptions.OPT_JSON.getName()));
    }
    try
    {
      ITeamRawRestServiceClient client = context.teamRepository().getRawRestServiceClient();
      URI uri = getURI(repo.getRepositoryURI(), parms);
      


      JSONObject codeReviewExportJson = null;
      

      Document codeReviewExportXml = null;
      
      boolean firstPass = true;
      
      while ((nextPageToken != null) && (!nextPageToken.isEmpty())) {
        ITeamRawRestServiceClient.IRawRestClientConnection connection = client.getConnection(uri);
        connection.addRequestHeader("Accept-Charset", HttpUtil.CharsetEncoding.UTF8.toString());
        connection.addRequestHeader("Accept", isXml ? HttpUtil.MediaType.XML.toString() : HttpUtil.MediaType.JSON.toString());
        ITeamRawRestServiceClient.IRawRestClientConnection.Response response = connection.doGet();
        int code = response.getStatusCode();
        if (code != 200) {
          throw new FileSystemException(NLS.bind(Messages.DebugFetchCmd_BAD_RESPONSE, Integer.valueOf(code)));
        }
        if (isXml)
        {
          Document responseDoc = newDocumentFromInputStream(response.getResponseStream());
          
          Element codeReviewElement = (Element)responseDoc.getElementsByTagName("value").item(0);
          NodeList nodes = codeReviewElement.getChildNodes();
          
          if (firstPass) {
            firstPass = false;
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            codeReviewExportXml = builder.newDocument();
            Element codeReviewExportElement = codeReviewExportXml.createElement("codeReviewExport");
            codeReviewExportXml.appendChild(codeReviewExportElement);
            Node workItemQuery = codeReviewExportXml.importNode(codeReviewElement.getElementsByTagName("workItemQuery").item(0), true);
            codeReviewExportXml.getElementsByTagName("codeReviewExport").item(0).appendChild(workItemQuery);
          }
          
          if (codeReviewExportXml != null) {
            for (int i = 0; i < nodes.getLength(); i++) {
              Node newNode = codeReviewExportXml.importNode(nodes.item(i), true);
              if (newNode.getNodeName().equals("codeReviews")) {
                codeReviewExportXml.getElementsByTagName("codeReviewExport").item(0).appendChild(newNode);
              }
            }
          }
          
          nextPageToken = codeReviewElement.getElementsByTagName("nextPageToken").item(0).getTextContent();
        }
        else {
          BufferedReader reader = new BufferedReader(new InputStreamReader(response.getResponseStream()));
          JSONObject responseJson = JSONObject.parse(reader);
          JSONObject values = (JSONObject)((JSONObject)((JSONObject)((JSONObject)responseJson.get("soapenv:Body")).get("response")).get("returnValue")).get("value");
          removePropertyFromJsonObject(values, "_eQualifiedClassName");
          
          if (firstPass) {
            firstPass = false;
            codeReviewExportJson = values;

          }
          else if (codeReviewExportJson != null) {
            JSONArray codeReviewsArray = (JSONArray)codeReviewExportJson.get("codeReviews");
            codeReviewsArray.addAll((JSONArray)values.get("codeReviews"));
          }
          

          nextPageToken = ((String)values.get("nextPageToken"));
        }
      }
      
      if (isXml) {
        xmlDumpToFile(file, codeReviewExportXml);
      } else {
        jsonDumpToFile(file, codeReviewExportJson);
      }
      
      config.getContext().stdout().println(Messages.ExportCodeReviewCmd_7);
    }
    catch (TeamRepositoryException e) {
      throw new FileSystemException(e);
    } catch (ParserConfigurationException e) {
      throw new FileSystemException(e);
    } catch (URISyntaxException e) {
      throw new FileSystemException(e);
    } catch (IOException e) {
      throw new FileSystemException(e);
    }
  }
  




  protected URI getURI(String repoURI, IFilesystemWorkItemRestService.ParmsGetCodeReviewReport parms)
    throws TeamRepositoryException
  {
    StringBuffer buffer = new StringBuffer();
    
    buffer.append(repoURI);
    if (!repoURI.endsWith("/")) {
      buffer.append("/");
    }
    buffer.append("service");
    buffer.append("/");
    buffer.append(IFilesystemWorkItemRestService.class.getName());
    
    buffer.append("/");
    buffer.append("codeReviewReport");
    buffer.append("?");
    buffer.append("queryId");
    buffer.append("=");
    buffer.append(queryId);
    buffer.append("&");
    buffer.append("includeCsDeliveryInfo");
    buffer.append("=");
    buffer.append(includeCsDeliveryInfo);
    buffer.append("&");
    buffer.append("nextPageToken");
    buffer.append("=");
    buffer.append(nextPageToken);
    try
    {
      return new URI(buffer.toString());
    } catch (URISyntaxException e) {
      throw new TeamRepositoryException(e);
    }
  }
  
  private void xmlDumpToFile(File file, Document codeReviewExportXml) throws FileSystemException {
    try {
      trimWhitespace(codeReviewExportXml.getFirstChild());
      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      Result output = new StreamResult(file);
      Source input = new DOMSource(codeReviewExportXml);
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
      transformer.setOutputProperty("indent", "yes");
      transformer.transform(input, output);
    } catch (TransformerException e) {
      throw new FileSystemException(e);
    }
  }
  
  private void jsonDumpToFile(File file, JSONObject codeReviewExportJson)
    throws IOException
  {
    ByteArrayInputStream input = new ByteArrayInputStream(codeReviewExportJson.toString().getBytes());
    OutputStream output = new BufferedOutputStream(new FileOutputStream(file));
    try
    {
      RepoUtil.transfer(new BufferedInputStream(input), output);
    }
    finally {
      try {
        output.close();
      }
      catch (IOException localIOException1) {}
    }
  }
  
  private static void removePropertyFromJsonObject(JSONObject json, String propertyToBeRemoved)
  {
    if (json.containsKey(propertyToBeRemoved)) {
      json.remove(propertyToBeRemoved);
    }
    for (Object property : json.keySet()) {
      if ((json.get((String)property) instanceof JSONArray)) {
        JSONArray json1 = (JSONArray)json.get((String)property);
        removePropertyFromJsonArray(json1, propertyToBeRemoved);
      } else if ((json.get((String)property) instanceof JSONObject)) {
        JSONObject json1 = (JSONObject)json.get((String)property);
        removePropertyFromJsonObject(json1, propertyToBeRemoved);
      }
    }
  }
  
  private static void removePropertyFromJsonArray(JSONArray jsonArray, String propertyToBeRemoved) {
    for (int i = 0; i < jsonArray.size(); i++) {
      if ((jsonArray.get(i) instanceof JSONArray)) {
        JSONArray json = (JSONArray)jsonArray.get(i);
        removePropertyFromJsonArray(json, propertyToBeRemoved);
      } else if ((jsonArray.get(i) instanceof JSONObject)) {
        JSONObject json = (JSONObject)jsonArray.get(i);
        removePropertyFromJsonObject(json, propertyToBeRemoved);
      }
    }
  }
  
  private static Document newDocumentFromInputStream(InputStream in) throws FileSystemException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      
      DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.parse(new InputSource(in));
    }
    catch (ParserConfigurationException e) {
      throw new FileSystemException(e);
    } catch (SAXException e) {
      throw new FileSystemException(e);
    } catch (IOException e) {
      throw new FileSystemException(e);
    }
  }
  
  private static void trimWhitespace(Node node) {
    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == 3) {
        child.setTextContent(child.getTextContent().trim());
      }
      trimWhitespace(child);
    }
  }
}
