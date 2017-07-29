package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineSetGetCustomAttributes;
import com.ibm.team.filesystem.common.internal.rest.client.workspace.BaselineCustomAttributesDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.osgi.util.NLS;








public class SnapshotShowCustomAttributesCmd
{
  IFilesystemRestClient client;
  
  public SnapshotShowCustomAttributesCmd() {}
  
  public static void getCustomAttributes(IScmCommandLineArgument ssSelector, IScmCommandLineArgument wsSelector, String key, boolean printCaption, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    long statusCode = 0L;
    String errorMsg = null;
    

    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, ssSelector);
    

    IWorkspace ws = null;
    if (wsSelector != null) {
      ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    }
    

    IBaselineSet ss = RepoUtil.getSnapshot(ws != null ? ws.getItemId().getUuidValue() : null, ssSelector.getItemSelector(), repo, config);
    

    JSONObject jSs = new JSONObject();
    try {
      JSONObject jProps = getCustomAttributes(ss, ws, key, repo, config, client);
      jSs.put("snapshot", ss.getName());
      jSs.put("properties", jProps);
    } catch (TeamRepositoryException e) {
      StatusHelper.wrap("", e, new IndentingPrintStream(config.getContext().stderr()));
      errorMsg = e.getLocalizedMessage();
      StatusHelper.logException(errorMsg, e);
    }
    
    if (errorMsg != null) {
      jSs.put("error-message", errorMsg);
    }
    
    jSs.put("status-code", Long.valueOf(statusCode));
    


    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jSs);
    } else {
      printProperties(jSs, printCaption, config);
    }
  }
  
  private static JSONObject getCustomAttributes(IBaselineSet ss, IWorkspace ws, String key, ITeamRepository repo, IScmClientConfiguration config, IFilesystemRestClient client)
    throws TeamRepositoryException
  {
    JSONObject jProps = new JSONObject();
    new IndentingPrintStream(config.getContext().stdout());
    
    ParmsBaselineSet parms = new ParmsBaselineSet();
    repositoryUrl = repo.getRepositoryURI();
    baselineSetItemId = ss.getItemId().getUuidValue();
    ParmsBaselineSetGetCustomAttributes parmsBl = new ParmsBaselineSetGetCustomAttributes();
    baselineSet = parms;
    

    BaselineCustomAttributesDTO customAttrs = client.getBaselineSetCustomAttributes(parmsBl, null);
    
    List<Map.Entry<String, String>> map = new ArrayList(customAttrs.getCustomAttributes().entrySet());
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
    

    jProps.put("uuid", ss.getItemId().getUuidValue());
    

    jProps.put("url", repo.getRepositoryURI());
    
    return jProps;
  }
  
  private static void printProperties(JSONObject jSs, boolean printCaption, IScmClientConfiguration config) throws FileSystemException
  {
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    

    JSONObject jProps = (JSONObject)jSs.get("properties");
    
    String itemId = (String)jProps.get("uuid");
    String repoUri = (String)jProps.get("url");
    

    String name = (String)jSs.get("snapshot");
    if (name != null) {
      name = AliasUtil.selector(name, UUID.valueOf(itemId), repoUri, RepoUtil.ItemType.SNAPSHOT);
      out.println(printCaption ? NLS.bind(Messages.WorkspacePropertiesCmd_NAME, name) : name);
    }
    

    JSONArray props = (JSONArray)jProps.get("custom_attributes");
    
    if (props != null) {
      int maxWidth = 0;
      JSONObject prop;
      for (Object entry : props) {
        prop = (JSONObject)entry;
        String property = (String)prop.get("property");
        
        maxWidth = Math.max(maxWidth, property.length());
      }
      
      IndentingPrintStream indent = out.indent();
      
      for (Object entry : props) {
        JSONObject prop = (JSONObject)entry;
        indent.println(NLS.bind(Messages.PropertyListCmd_KeyValue, 
          StringUtil.pad((String)prop.get("property"), maxWidth), 
          (String)prop.get("value")));
      }
    }
  }
}
