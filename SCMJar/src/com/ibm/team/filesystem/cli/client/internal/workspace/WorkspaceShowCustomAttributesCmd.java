package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.workspace.WorkspaceCustomAttributesDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.osgi.util.NLS;







public class WorkspaceShowCustomAttributesCmd
{
  public WorkspaceShowCustomAttributesCmd() {}
  
  protected static boolean hasAllWorkspaceProperties(JSONArray jWsArray)
  {
    for (Object obj : jWsArray) {
      JSONObject jWs = (JSONObject)obj;
      
      Long statusCode = (Long)jWs.get("status-code");
      if (statusCode.longValue() != 0L) {
        return false;
      }
    }
    
    return true;
  }
  
  public static void showCustomAttributes(List<IScmCommandLineArgument> wsSelectorList, String propertyName, String key, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    JSONArray jWsArray = new JSONArray();
    for (IScmCommandLineArgument wsSelector : wsSelectorList) {
      JSONObject jWs = jsonizeWorkspaces(wsSelector, key, client, config);
      jWsArray.add(jWs);
    }
    
    boolean retrievedWorkspaceProperties;
    boolean retrievedWorkspaceProperties;
    if (config.isJSONEnabled()) {
      if (!jWsArray.isEmpty()) {
        JSONObject jResult = new JSONObject();
        jResult.put("workspaces", jWsArray);
        config.getContext().stdout().print(jResult);
      }
      retrievedWorkspaceProperties = hasAllWorkspaceProperties(jWsArray);
    } else {
      retrievedWorkspaceProperties = printWorkspaces(jWsArray, propertyName == null, config);
    }
    
    if (!retrievedWorkspaceProperties) {
      throw StatusHelper.propertiesUnavailable(Messages.WorkspaceCustomAttributesCmd_PROPERTY_LIST_FAILURE);
    }
  }
  
  private static JSONObject jsonizeWorkspaces(IScmCommandLineArgument wsSelector, String key, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    JSONObject jWs = new JSONObject();
    long statusCode = 0L;
    String errorMsg = null;
    ITeamRepository repo = null;
    IWorkspace ws = null;
    
    try
    {
      repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      
      ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
      
      JSONObject jProps = getCustomAttributes(ws, key, repo, client, config);
      jWs.put("workspace", ws.getName());
      jWs.put("properties", jProps);
    } catch (CLIFileSystemClientException e) {
      statusCode = e.getStatus().getCode();
      errorMsg = e.getLocalizedMessage();
      StatusHelper.logException(errorMsg, e);
    } catch (TeamRepositoryException e) {
      CLIFileSystemClientException exp = StatusHelper.wrap("", e, new IndentingPrintStream(config.getContext().stderr()));
      statusCode = exp.getStatus().getCode();
      errorMsg = e.getLocalizedMessage();
      StatusHelper.logException(errorMsg, e);
    } catch (Exception e) {
      statusCode = 3L;
      errorMsg = e.getLocalizedMessage();
      StatusHelper.logException(errorMsg, e);
    }
    
    if (ws == null) {
      jWs.put("workspace", wsSelector.getItemSelector());
    }
    if (errorMsg != null) {
      jWs.put("error-message", errorMsg);
    }
    
    jWs.put("status-code", Long.valueOf(statusCode));
    
    return jWs;
  }
  
  protected static JSONObject getCustomAttributes(IWorkspace ws, String key, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config) throws TeamRepositoryException
  {
    JSONObject jProps = new JSONObject();
    

    ParmsWorkspace parmsWs = new ParmsWorkspace(repo.getRepositoryURI(), ws.getItemId().getUuidValue());
    

    WorkspaceCustomAttributesDTO wsCustomAttrs = client.getWorkspaceCustomAttributes(parmsWs, null);
    WorkspaceDetailsDTO wsDetails = wsCustomAttrs.getWorkspace();
    

    jProps.put("name", wsDetails.getName());
    
    jProps.put("uuid", wsDetails.getItemId());
    
    jProps.put("url", wsDetails.getRepositoryURL());
    
    jProps.put("type", wsDetails.isStream() ? RepoUtil.ItemType.STREAM.toString() : RepoUtil.ItemType.WORKSPACE.toString());
    

    List<Map.Entry<String, String>> map = new ArrayList(wsCustomAttrs.getCustomAttributes().entrySet());
    JSONArray props = new JSONArray();
    for (Map.Entry<String, String> entry : map) {
      JSONObject prop = new JSONObject();
      if ((key == null) || (key.equals(entry.getKey()))) {
        prop.put("property", entry.getKey());
        prop.put("value", entry.getValue());
        props.add(prop);
      }
    }
    
    if (props.size() > 0) {
      jProps.put("custom_attributes", props);
    }
    
    return jProps;
  }
  
  private static boolean printWorkspaces(JSONArray jWsArray, boolean printCaption, IClientConfiguration config) throws FileSystemException
  {
    boolean retrievedAllWorkspaceProperties = true;
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
    
    for (Object obj : jWsArray) {
      JSONObject jWs = (JSONObject)obj;
      boolean result = printWorkspace(jWs, printCaption, out, err);
      if (!result) {
        retrievedAllWorkspaceProperties = false;
      }
    }
    
    return retrievedAllWorkspaceProperties;
  }
  

  public static boolean printWorkspace(JSONObject jWs, boolean printCaption, IndentingPrintStream out, IndentingPrintStream err)
  {
    String ws = (String)jWs.get("workspace");
    
    long statusCode = ((Long)jWs.get("status-code")).longValue();
    if (statusCode != 0L)
    {
      String errorMsg = (String)jWs.get("error-message");
      err.println(ws);
      err.indent().println(NLS.bind(Messages.Common_ERROR_CODE, Long.valueOf(statusCode)));
      err.indent().println(NLS.bind(Messages.Common_ERROR_MESSAGE, errorMsg));
      return false;
    }
    

    JSONObject jProps = (JSONObject)jWs.get("properties");
    
    String name = (String)jProps.get("name");
    String itemId = (String)jProps.get("uuid");
    String repoUri = (String)jProps.get("url");
    RepoUtil.ItemType itemType = RepoUtil.ItemType.valueOf((String)jProps.get("type"));
    

    String wsTitle = AliasUtil.selector(name, UUID.valueOf(itemId), repoUri, itemType);
    out.println(wsTitle);
    
    printCustomAttibutes(jProps, out.indent());
    
    return true;
  }
  
  private static void printCustomAttibutes(JSONObject fileProp, IndentingPrintStream out)
  {
    if (fileProp == null) {
      return;
    }
    
    JSONArray props = (JSONArray)fileProp.get("custom_attributes");
    
    if (props == null) {
      return;
    }
    
    int maxWidth = 0;
    JSONObject prop;
    for (Object entry : props) {
      prop = (JSONObject)entry;
      String property = (String)prop.get("property");
      
      maxWidth = Math.max(maxWidth, property.length());
    }
    
    IndentingPrintStream indent = out.indent();
    
    for (Object entry : props)
    {
      JSONObject prop = (JSONObject)entry;
      indent.println(NLS.bind(Messages.PropertyListCmd_KeyValue, 
        StringUtil.pad((String)prop.get("property"), maxWidth), 
        prop.get("value")));
    }
  }
}
