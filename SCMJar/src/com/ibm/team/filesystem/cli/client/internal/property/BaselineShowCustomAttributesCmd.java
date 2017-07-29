package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaseline;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineCustomAttributes;
import com.ibm.team.filesystem.common.internal.rest.client.workspace.BaselineCustomAttributesDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IComponent;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.osgi.util.NLS;





public class BaselineShowCustomAttributesCmd
{
  IFilesystemRestClient client;
  
  public BaselineShowCustomAttributesCmd() {}
  
  protected void getCustomAttributes(String key, IScmClientConfiguration config)
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    

    client = SubcommandUtil.setupDaemon(config);
    
    IScmCommandLineArgument blSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowCustomAttributesCmd.OPT_BASELINE), config);
    SubcommandUtil.validateArgument(blSelector, RepoUtil.ItemType.BASELINE);
    
    IScmCommandLineArgument compSelector = null;
    if (cli.hasOption(ShowCustomAttributesCmd.OPT_COMPONENT)) {
      compSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowCustomAttributesCmd.OPT_COMPONENT), config);
      SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
    }
    
    getCustomAttributes(blSelector, compSelector, key, client, config);
  }
  

  public static void getCustomAttributes(IScmCommandLineArgument blSelector, IScmCommandLineArgument compSelector, String key, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, blSelector);
    long statusCode = 0L;
    String errorMsg = null;
    

    IComponent comp = null;
    if (compSelector != null) {
      comp = RepoUtil.getComponent(compSelector.getItemSelector(), repo, config);
    }
    

    IBaseline bl = RepoUtil.getBaseline(blSelector.getItemSelector(), comp != null ? comp.getItemId().getUuidValue() : null, 
      comp != null ? comp.getName() : null, repo, client, config);
    
    JSONObject jBl = new JSONObject();
    try
    {
      JSONObject jProps = getCustomAttributes(bl, key, repo, config, client);
      jBl.put("baseline", bl.getName());
      jBl.put("properties", jProps);
    } catch (TeamRepositoryException e) {
      CLIFileSystemClientException exp = StatusHelper.wrap("", e, new IndentingPrintStream(config.getContext().stderr()));
      statusCode = exp.getStatus().getCode();
      errorMsg = e.getLocalizedMessage();
      StatusHelper.logException(errorMsg, e);
    }
    
    if (errorMsg != null) {
      jBl.put("error-message", errorMsg);
    }
    
    jBl.put("status-code", Long.valueOf(statusCode));
    


    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jBl);
    } else {
      printProperties(jBl, config);
    }
  }
  
  private static JSONObject getCustomAttributes(IBaseline bl, String key, ITeamRepository repo, IScmClientConfiguration config, IFilesystemRestClient client) throws TeamRepositoryException
  {
    JSONObject jProps = new JSONObject();
    new IndentingPrintStream(config.getContext().stdout());
    

    jProps.put("name", bl.getName());
    

    jProps.put("description", bl.getComment());
    

    jProps.put("uuid", bl.getItemId().getUuidValue());
    

    jProps.put("url", repo.getRepositoryURI());
    

    ParmsBaseline parmsBl = new ParmsBaseline();
    repositoryUrl = repo.getRepositoryURI();
    baselineItemId = bl.getItemId().getUuidValue();
    ParmsBaselineCustomAttributes parms = new ParmsBaselineCustomAttributes();
    baseline = parmsBl;
    

    BaselineCustomAttributesDTO customAttrs = client.getBaselineCustomAttributes(parms, null);
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
    
    return jProps;
  }
  
  private static void printProperties(JSONObject jBl, IScmClientConfiguration config) throws FileSystemException
  {
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    

    JSONObject jProps = (JSONObject)jBl.get("properties");
    
    String itemId = (String)jProps.get("uuid");
    String repoUri = (String)jProps.get("url");
    

    String name = (String)jProps.get("name");
    if (name != null) {
      name = AliasUtil.selector(name, UUID.valueOf(itemId), repoUri, RepoUtil.ItemType.BASELINE);
      out.println(name);
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
