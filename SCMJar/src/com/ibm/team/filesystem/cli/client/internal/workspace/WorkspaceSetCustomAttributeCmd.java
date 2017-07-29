package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsProperty;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceCustomAttributes;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import org.eclipse.osgi.util.NLS;







public class WorkspaceSetCustomAttributeCmd
{
  public WorkspaceSetCustomAttributeCmd() {}
  
  protected static boolean checkReturnStatus(JSONArray jWsArray)
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
  
  public static void setCustomAttributes(List<IScmCommandLineArgument> wsSelectorList, Map<String, String> customAttrs, IFilesystemRestClient client, IScmClientConfiguration config)
    throws TeamRepositoryException
  {
    JSONArray jWsArray = new JSONArray();
    for (IScmCommandLineArgument wsSelector : wsSelectorList) {
      JSONObject jWs = setCustomAttributes(wsSelector, customAttrs, client, config);
      jWsArray.add(jWs);
    }
    boolean setAllWorkspaceProperties;
    boolean setAllWorkspaceProperties;
    if (config.isJSONEnabled()) {
      if (!jWsArray.isEmpty()) {
        JSONObject jResult = new JSONObject();
        jResult.put("workspaces", jWsArray);
        config.getContext().stdout().print(jResult);
      }
      setAllWorkspaceProperties = checkReturnStatus(jWsArray);
    } else {
      setAllWorkspaceProperties = printResult(jWsArray, config);
      if (setAllWorkspaceProperties) {
        config.getContext().stdout().println(Messages.WorkspaceCustomAttributesCmd_PROPERTY_SET_SUCCESS);
      }
    }
    
    if (!setAllWorkspaceProperties) {
      throw StatusHelper.propertiesUnavailable(Messages.WorkspaceCustomAttributesCmd_PROPERTY_SET_FAILURE);
    }
  }
  
  private static JSONObject setCustomAttributes(IScmCommandLineArgument wsSelector, Map<String, String> customAttrs, IFilesystemRestClient client, IScmClientConfiguration config)
    throws TeamRepositoryException
  {
    JSONObject jWs = new JSONObject();
    String errorMsg = null;
    long statusCode = 0L;
    ITeamRepository repo = null;
    IWorkspace ws = null;
    boolean success = false;
    

    repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
    
    ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    jWs.put("workspace", ws.getName());
    jWs.put("uuid", ws.getItemId().getUuidValue());
    jWs.put("url", repo.getRepositoryURI());
    jWs.put("type", ws.isStream() ? RepoUtil.ItemType.STREAM.toString() : RepoUtil.ItemType.WORKSPACE.toString());
    
    setCustomAttrs(ws, customAttrs, repo, client, config);
    success = true;
    
    if (ws == null) {
      jWs.put("workspace", wsSelector.getItemSelector());
    }
    
    jWs.put("status-code", Long.valueOf(statusCode));
    if (!success) {
      jWs.put("error-message", errorMsg);
    }
    
    StringBuffer propertyNames = new StringBuffer();
    for (String property : customAttrs.keySet()) {
      if (propertyNames.length() != 0) {
        propertyNames.append(", ");
      }
      propertyNames.append(property);
    }
    jWs.put("property", propertyNames.toString());
    
    return jWs;
  }
  
  public static void setCustomAttrs(IWorkspace ws, Map<String, String> customAttrs, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws TeamRepositoryException
  {
    ParmsWorkspaceCustomAttributes parms = new ParmsWorkspaceCustomAttributes();
    workspace = new ParmsWorkspace(repo.getRepositoryURI(), ws.getItemId().getUuidValue());
    

    ParmsProperty[] properties = new ParmsProperty[customAttrs.size()];
    int i = 0;
    for (String key : customAttrs.keySet()) {
      ParmsProperty prop = new ParmsProperty(key, (String)customAttrs.get(key));
      properties[(i++)] = prop;
    }
    setCustomAttrs = properties;
    

    client.postWorkspaceCustomAttributes(parms, null);
  }
  
  private static boolean printResult(JSONArray jWsArray, IClientConfiguration config) {
    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
    boolean setAllWorkspaceProperties = true;
    
    for (Object obj : jWsArray) {
      JSONObject jWs = (JSONObject)obj;
      

      String ws = (String)jWs.get("workspace");
      if (jWs.get("uuid") != null) {
        String wsItemId = (String)jWs.get("uuid");
        String repoUri = (String)jWs.get("url");
        RepoUtil.ItemType itemType = RepoUtil.ItemType.valueOf((String)jWs.get("type"));
        ws = AliasUtil.selector(ws, UUID.valueOf(wsItemId), repoUri, itemType);
      }
      
      long statusCode = ((Long)jWs.get("status-code")).longValue();
      if (statusCode != 0L)
      {
        String errorMsg = (String)jWs.get("error-message");
        err.println(ws);
        err.indent().println(NLS.bind(Messages.Common_ERROR_CODE, Long.valueOf(statusCode)));
        err.indent().println(NLS.bind(Messages.Common_ERROR_MESSAGE, errorMsg));
        setAllWorkspaceProperties = false;
      }
    }
    

    return setAllWorkspaceProperties;
  }
}
