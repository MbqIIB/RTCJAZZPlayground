package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IComponent;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.osgi.util.NLS;






public class BaselinePropertyListCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  IFilesystemRestClient client;
  public static final String NAME_PROPERTY = "name";
  public static final String DESCRIPTION_PROPERTY = "description";
  public static final String DESCRIPTION_ALIAS_PROPERTY = "desc";
  
  public BaselinePropertyListCmd() {}
  
  public static final String[] PROPERTIES = { "name", "description", "desc" };
  
  public static final PositionalOptionDefinition OPT_BASELINE_SELECTOR = new PositionalOptionDefinition("baseline", 1, 1, "@");
  public static final NamedOptionDefinition OPT_COMPONENT_SELECTOR = new NamedOptionDefinition("c", "component", 1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(OPT_BASELINE_SELECTOR, Messages.BaselinePropertiesCmdOptions_BASELINE_LIST_HELP)
      .addOption(OPT_COMPONENT_SELECTOR, Messages.BaselinePropertiesCmdOptions_COMPONENT_HELP)
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT);
    
    return options;
  }
  
  public void run() throws FileSystemException
  {
    listProperties(null);
  }
  
  protected void listProperties(String propertyName) throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    if ((propertyName != null) && (!Arrays.asList(PROPERTIES).contains(propertyName))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_PROPERTY_NAME, propertyName));
    }
    

    client = SubcommandUtil.setupDaemon(config);
    
    IScmCommandLineArgument blSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_BASELINE_SELECTOR), config);
    SubcommandUtil.validateArgument(blSelector, RepoUtil.ItemType.BASELINE);
    
    IScmCommandLineArgument compSelector = null;
    if (cli.hasOption(OPT_COMPONENT_SELECTOR)) {
      compSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT_SELECTOR), config);
      SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
    }
    
    List<String> propertyNames = null;
    if (propertyName != null) {
      propertyNames = new ArrayList(1);
      propertyNames.add(propertyName.toLowerCase());
    }
    
    listProperties(blSelector, compSelector, propertyNames, propertyName == null, client, config);
  }
  

  public static void listProperties(IScmCommandLineArgument blSelector, IScmCommandLineArgument compSelector, List<String> propertyNames, boolean printCaption, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, blSelector);
    

    IComponent comp = null;
    if (compSelector != null) {
      comp = RepoUtil.getComponent(compSelector.getItemSelector(), repo, config);
    }
    

    IBaseline bl = RepoUtil.getBaseline(blSelector.getItemSelector(), comp != null ? comp.getItemId().getUuidValue() : null, 
      comp != null ? comp.getName() : null, repo, client, config);
    

    JSONObject jBl = new JSONObject();
    JSONObject jProps = getProperties(bl, propertyNames, repo, config);
    jBl.put("baseline", bl.getName());
    jBl.put("properties", jProps);
    

    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jBl);
    } else {
      printProperties(jBl, printCaption, config);
    }
  }
  
  private static JSONObject getProperties(IBaseline bl, List<String> propertyNames, ITeamRepository repo, IScmClientConfiguration config)
    throws FileSystemException
  {
    JSONObject jProps = new JSONObject();
    new IndentingPrintStream(config.getContext().stdout());
    

    if ((propertyNames == null) || (propertyNames.contains("name"))) {
      jProps.put("name", bl.getName());
    }
    

    if ((propertyNames == null) || (propertyNames.contains("description")) || (propertyNames.contains("desc"))) {
      jProps.put("description", bl.getComment());
    }
    

    jProps.put("uuid", bl.getItemId().getUuidValue());
    

    jProps.put("url", repo.getRepositoryURI());
    
    return jProps;
  }
  
  private static void printProperties(JSONObject jBl, boolean printCaption, IScmClientConfiguration config) throws FileSystemException
  {
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    

    JSONObject jProps = (JSONObject)jBl.get("properties");
    
    String itemId = (String)jProps.get("uuid");
    String repoUri = (String)jProps.get("url");
    

    String name = (String)jProps.get("name");
    if (name != null) {
      name = AliasUtil.selector(name, UUID.valueOf(itemId), repoUri, RepoUtil.ItemType.BASELINE);
      out.println(printCaption ? NLS.bind(Messages.WorkspacePropertiesCmd_NAME, name) : name);
    }
    

    String description = (String)jProps.get("description");
    if ((description != null) && (!description.isEmpty())) {
      if (printCaption) {
        out.println(Messages.WorkspacePropertiesCmd_DESCRIPTION);
      }
      out.println(description);
    }
  }
}
