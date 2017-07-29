package com.ibm.team.filesystem.cli.client.internal.snapshot;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.property.SetAttributesCmd;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineSetRelease;
import com.ibm.team.filesystem.client.rest.parameters.ParmsItemHandle;
import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.workitem.common.model.IDeliverable;
import java.io.PrintStream;
import org.eclipse.osgi.util.NLS;






public class AddReleaseToSnapshotCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public static final NamedOptionDefinition OPT_RELEASE = new NamedOptionDefinition(null, "release", 1);
  
  public static final PositionalOptionDefinition OPT_SNAPSHOT = new PositionalOptionDefinition("snapshot", 0, 1, "@");
  
  public AddReleaseToSnapshotCmd() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(OPT_RELEASE, Messages.AddReleaseToSnapshotCmd_RELEASE_HELP, false)
      .addOption(SetAttributesCmd.OPT_PROJECTAREA, Messages.SnapshotPropertiesCmdOptions_PROJECTAREA_HELP)
      .addOption(OPT_SNAPSHOT, Messages.AddReleaseToSnapshotCmd_SNAPSHOT_TO_ADD);
    
    return options;
  }
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    if (!cli.hasOption(OPT_RELEASE)) {
      throw StatusHelper.argSyntax(Messages.AddReleaseToSnapshotCmd_SPECIFY_RELEASE);
    }
    
    IScmCommandLineArgument ssSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_SNAPSHOT, null), config);
    
    IScmCommandLineArgument release = 
      ScmCommandLineArgument.create(cli.getOptionValue(OPT_RELEASE), config);
    
    IScmCommandLineArgument projectArea = null;
    if (cli.hasOption(SetAttributesCmd.OPT_PROJECTAREA)) {
      projectArea = ScmCommandLineArgument.create(cli.getOptionValue(SetAttributesCmd.OPT_PROJECTAREA), config);
      SubcommandUtil.validateArgument(projectArea, new RepoUtil.ItemType[] { RepoUtil.ItemType.PROJECTAREA, RepoUtil.ItemType.TEAMAREA });
    }
    

    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, ssSelector);
    

    IBaselineSet ss = RepoUtil.getSnapshot(null, ssSelector.getItemSelector(), repo, config);
    

    setProperties(ss, release, projectArea, repo, client, config);
    
    config.getContext().stdout().println(NLS.bind(Messages.AddReleaseToSnapshotCmd_SUCCESS, release.getItemSelector()));
  }
  
  private static void setProperties(IBaselineSet ss, IScmCommandLineArgument releaseArg, IScmCommandLineArgument projectArea, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ParmsBaselineSetRelease parms = new ParmsBaselineSetRelease();
    baselineSet = new ParmsBaselineSet();
    baselineSet.baselineSetItemId = ss.getItemId().getUuidValue();
    baselineSet.repositoryUrl = repo.getRepositoryURI();
    


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
    
    IDeliverable release = RepoUtil.getReleaseByName(releaseArg.getItemSelector(), processArea.getProjectArea(), repo, config);
    
    release = new ParmsItemHandle(release.getItemHandle());
    


    try
    {
      client.postAssociateBaselineSetWithRelease(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(NLS.bind(Messages.AddReleaseToSnapshotCmd_FAILURE, releaseArg.getItemSelector()), e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
  }
}
