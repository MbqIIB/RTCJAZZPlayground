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
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsItemHandle;
import com.ibm.team.filesystem.client.rest.parameters.ParmsUpdateBaselineSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.process.common.IIterationHandle;
import com.ibm.team.process.common.IProcessArea;
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
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.osgi.util.NLS;





public class SnapshotPropertySetCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  IFilesystemRestClient client;
  public static final PositionalOptionDefinition OPT_PROPERTY_NAME = new PositionalOptionDefinition("property-name", 1, 1);
  public static final PositionalOptionDefinition OPT_PROPERTY_VALUE = new PositionalOptionDefinition("property-value", 1, 1);
  
  public SnapshotPropertySetCmd() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(OPT_PROPERTY_NAME, NLS.bind(Messages.SnapshotPropertiesCmdOptions_OPT_PROPERTY_SET_NAME_HELP, 
      new String[] { "name", "ownedby", 
      "owned", "description", 
      "desc", "iteration" }))
      .addOption(OPT_PROPERTY_VALUE, Messages.SnapshotPropertiesCmdOptions_OPT_PROPERTY_VALUE_HELP)
      .addOption(SnapshotPropertyListCmd.OPT_SNAPSHOT_SELECTOR, Messages.SnapshotPropertiesCmdOptions_SNAPSHOT_SET_HELP)
      .addOption(SnapshotPropertyListCmd.OPT_WORKSPACE_SELECTOR, Messages.SnapshotPropertiesCmdOptions_WORKSPACE_HELP)
      .addOption(SetAttributesCmd.OPT_PROJECTAREA, Messages.SnapshotPropertiesCmdOptions_PROJECTAREA_HELP);
    
    return options;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    String propertyName = cli.getOption(OPT_PROPERTY_NAME, null);
    if ((propertyName != null) && (!Arrays.asList(SnapshotPropertyListCmd.PROPERTIES).contains(propertyName))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_PROPERTY_NAME, propertyName));
    }
    

    client = SubcommandUtil.setupDaemon(config);
    
    IScmCommandLineArgument ssSelector = ScmCommandLineArgument.create(cli.getOptionValue(SnapshotPropertyListCmd.OPT_SNAPSHOT_SELECTOR), config);
    SubcommandUtil.validateArgument(ssSelector, RepoUtil.ItemType.SNAPSHOT);
    
    IScmCommandLineArgument propertyValue = ScmCommandLineArgument.create(cli.getOptionValue(OPT_PROPERTY_VALUE, null), config);
    
    IScmCommandLineArgument wsSelector = null;
    if (cli.hasOption(SnapshotPropertyListCmd.OPT_WORKSPACE_SELECTOR)) {
      wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(SnapshotPropertyListCmd.OPT_WORKSPACE_SELECTOR), config);
      SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    }
    
    IScmCommandLineArgument projectArea = null;
    if (cli.hasOption(SetAttributesCmd.OPT_PROJECTAREA)) {
      projectArea = ScmCommandLineArgument.create(cli.getOptionValue(SetAttributesCmd.OPT_PROJECTAREA), config);
      SubcommandUtil.validateArgument(projectArea, new RepoUtil.ItemType[] { RepoUtil.ItemType.PROJECTAREA, RepoUtil.ItemType.TEAMAREA });
    }
    
    Map<String, IScmCommandLineArgument> properties = new HashMap();
    properties.put(propertyName.toLowerCase(), propertyValue);
    
    setProperties(ssSelector, wsSelector, projectArea, properties, client, config);
  }
  

  public static void setProperties(IScmCommandLineArgument ssSelector, IScmCommandLineArgument wsSelector, IScmCommandLineArgument projectArea, Map<String, IScmCommandLineArgument> properties, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, ssSelector);
    

    IWorkspace ws = null;
    if (wsSelector != null) {
      ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    }
    

    IBaselineSet ss = RepoUtil.getSnapshot(ws != null ? ws.getItemId().getUuidValue() : null, ssSelector.getItemSelector(), repo, config);
    

    setProperties(ss, properties, projectArea, repo, client, config);
    
    config.getContext().stdout().println(Messages.SnapshotPropertiesCmd_PROPERTY_SET_SUCCESS);
  }
  
  private static void setProperties(IBaselineSet ss, Map<String, IScmCommandLineArgument> properties, IScmCommandLineArgument projectArea, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ParmsUpdateBaselineSet parms = new ParmsUpdateBaselineSet();
    baselineSet = new ParmsBaselineSet();
    baselineSet.baselineSetItemId = ss.getItemId().getUuidValue();
    baselineSet.repositoryUrl = repo.getRepositoryURI();
    
    if (properties.containsKey("name")) {
      name = ((IScmCommandLineArgument)properties.get("name")).getItemSelector();
    }
    
    if ((properties.containsKey("ownedby")) || 
      (properties.containsKey("owned"))) {
      IScmCommandLineArgument propertyValue = (IScmCommandLineArgument)properties.get("ownedby");
      if (propertyValue == null) {
        propertyValue = (IScmCommandLineArgument)properties.get("owned");
      }
      SubcommandUtil.validateArgument(propertyValue, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      
      ITeamRepository repo2 = RepoUtil.loginUrlArgAncestor(config, client, propertyValue);
      IWorkspace targetWs = RepoUtil.getWorkspace(propertyValue.getItemSelector(), true, true, repo, config);
      promotionWorkspace = new ParmsWorkspace(repo2.getRepositoryURI(), targetWs.getItemId().getUuidValue());
    }
    
    if ((properties.containsKey("description")) || 
      (properties.containsKey("desc"))) {
      IScmCommandLineArgument propertyValue = (IScmCommandLineArgument)properties.get("description");
      if (propertyValue == null) {
        propertyValue = (IScmCommandLineArgument)properties.get("desc");
      }
      
      comment = propertyValue.getItemSelector();
    }
    

    if (properties.containsKey("iteration")) {
      IProcessArea processArea = null;
      if (projectArea != null)
      {
        SubcommandUtil.validateArgument(projectArea, new RepoUtil.ItemType[] { RepoUtil.ItemType.PROJECTAREA, RepoUtil.ItemType.TEAMAREA });
        

        processArea = RepoUtil.getProcessArea(projectArea, null, repo, config);
        if (processArea == null) {
          throw StatusHelper.inappropriateArgument(NLS.bind(Messages.ListCmd_NOPROJECTAREA, 
            projectArea.getItemSelector()));
        }
      } else {
        processArea = RepoUtil.getSnapshotProjectArea(ss, repo, config);
      }
      IScmCommandLineArgument propertyValue = (IScmCommandLineArgument)properties.get("iteration");
      
      IIterationHandle iteration = RepoUtil.getProcessAreaIterationByName(propertyValue.getItemSelector(), processArea.getProjectArea(), repo, config);
      
      iteration = new ParmsItemHandle(iteration);
    }
    

    try
    {
      client.postUpdateBaselineSet(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.SnapshotPropertiesCmd_PROPERTY_SET_FAILURE, e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
  }
}
