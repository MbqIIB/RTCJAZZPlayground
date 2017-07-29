package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaseline;
import com.ibm.team.filesystem.client.rest.parameters.ParmsUpdateBaseline;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IComponent;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.osgi.util.NLS;


public class BaselinePropertySetCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  IFilesystemRestClient client;
  
  public BaselinePropertySetCmd() {}
  
  public static final PositionalOptionDefinition OPT_PROPERTY_NAME = new PositionalOptionDefinition("property-name", 1, 1);
  public static final PositionalOptionDefinition OPT_PROPERTY_VALUE = new PositionalOptionDefinition("property-value", 1, 1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(OPT_PROPERTY_NAME, NLS.bind(Messages.BaselinePropertiesCmdOptions_OPT_PROPERTY_SET_NAME_HELP, 
      new String[] { "name", "description", "desc" }))
      .addOption(OPT_PROPERTY_VALUE, Messages.BaselinePropertiesCmdOptions_OPT_PROPERTY_VALUE_HELP)
      .addOption(BaselinePropertyListCmd.OPT_BASELINE_SELECTOR, Messages.BaselinePropertiesCmdOptions_BASELINE_SET_HELP)
      .addOption(BaselinePropertyListCmd.OPT_COMPONENT_SELECTOR, Messages.BaselinePropertiesCmdOptions_COMPONENT_HELP);
    
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    String propertyName = cli.getOption(OPT_PROPERTY_NAME, null);
    if ((propertyName != null) && (!Arrays.asList(BaselinePropertyListCmd.PROPERTIES).contains(propertyName))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_PROPERTY_NAME, propertyName));
    }
    

    client = SubcommandUtil.setupDaemon(config);
    
    IScmCommandLineArgument blSelector = ScmCommandLineArgument.create(cli.getOptionValue(BaselinePropertyListCmd.OPT_BASELINE_SELECTOR), config);
    SubcommandUtil.validateArgument(blSelector, RepoUtil.ItemType.BASELINE);
    
    IScmCommandLineArgument compSelector = null;
    if (cli.hasOption(BaselinePropertyListCmd.OPT_COMPONENT_SELECTOR)) {
      compSelector = ScmCommandLineArgument.create(cli.getOptionValue(BaselinePropertyListCmd.OPT_COMPONENT_SELECTOR), config);
      SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
    }
    
    IScmCommandLineArgument propertyValue = ScmCommandLineArgument.create(cli.getOptionValue(OPT_PROPERTY_VALUE, null), config);
    Map<String, IScmCommandLineArgument> properties = new HashMap();
    properties.put(propertyName.toLowerCase(), propertyValue);
    
    setProperties(blSelector, compSelector, properties, client, config);
  }
  

  public static void setProperties(IScmCommandLineArgument blSelector, IScmCommandLineArgument compSelector, Map<String, IScmCommandLineArgument> properties, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, blSelector);
    

    IComponent comp = null;
    if (compSelector != null) {
      comp = RepoUtil.getComponent(compSelector.getItemSelector(), repo, config);
    }
    

    IBaseline bl = RepoUtil.getBaseline(blSelector.getItemSelector(), comp != null ? comp.getItemId().getUuidValue() : null, 
      comp != null ? comp.getName() : null, repo, client, config);
    

    setProperties(bl, properties, repo, client, config);
    
    config.getContext().stdout().println(Messages.BaselinePropertiesCmd_PROPERTY_SET_SUCCESS);
  }
  
  private static void setProperties(IBaseline bl, Map<String, IScmCommandLineArgument> properties, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ParmsUpdateBaseline parms = new ParmsUpdateBaseline();
    baseline = new ParmsBaseline();
    baseline.baselineItemId = bl.getItemId().getUuidValue();
    baseline.repositoryUrl = repo.getRepositoryURI();
    
    if (properties.containsKey("name")) {
      name = ((IScmCommandLineArgument)properties.get("name")).getItemSelector();
    } else if ((properties.containsKey("description")) || 
      (properties.containsKey("desc"))) {
      IScmCommandLineArgument propertyValue = (IScmCommandLineArgument)properties.get("description");
      if (propertyValue == null) {
        propertyValue = (IScmCommandLineArgument)properties.get("desc");
      }
      
      comment = propertyValue.getItemSelector();
    }
    

    try
    {
      client.postUpdateBaseline(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.BaselinePropertiesCmd_PROPERTY_SET_FAILURE, e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
  }
}
