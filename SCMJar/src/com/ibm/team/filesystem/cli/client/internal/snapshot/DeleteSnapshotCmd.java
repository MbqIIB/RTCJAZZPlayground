package com.ibm.team.filesystem.cli.client.internal.snapshot;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsRemoveBaselineSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.repository.common.util.NLS;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class DeleteSnapshotCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public DeleteSnapshotCmd() {}
  
  public static final IPositionalOptionDefinition OPT_SNAPSHOTS = new PositionalOptionDefinition(CommonOptions.OPT_SNAPSHOTS, "snapshots", 0, -1);
  public static final IPositionalOptionDefinition OPT_TARGET = new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_VERBOSE, Messages.DeleteSnapshotCmdOpts_OPT_VERBOSE)
      .addOption(OPT_TARGET, Messages.ReplaceComponentsCmdOptions_TARGET_WORKSPACE)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(CommonOptions.OPT_ALL, Messages.DeleteSnapshotCmdOpts_OPT_ALL_HELP, false)
      .addOption(OPT_SNAPSHOTS, Messages.DeleteSnapshotCmdOpts_OPT_SNAPSHOTS_HELP));
    
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    if ((cli.hasOption(OPT_SNAPSHOTS.getId())) && (cli.hasOption(CommonOptions.OPT_ALL))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.DeleteSnapshotCmd_SNAPSHOTS_OR_ALL, OPT_SNAPSHOTS.getName(), new Object[] { CommonOptions.OPT_ALL.getName() }));
    }
    if ((!cli.hasOption(OPT_SNAPSHOTS.getId())) && (!cli.hasOption(CommonOptions.OPT_ALL))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.DeleteSnapshotCmd_SPECIFY_SNAPSHOTS_OR_ALL, OPT_SNAPSHOTS.getName(), new Object[] { CommonOptions.OPT_ALL.getName() }));
    }
    

    IScmCommandLineArgument target = ScmCommandLineArgument.create(cli.getOptionValue(OPT_TARGET.getId()), config);
    SubcommandUtil.validateArgument(target, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, target);
    
    IWorkspace targetWs = RepoUtil.getWorkspace(target.getItemSelector(), true, true, repo, config);
    

    List<IBaselineSet> snapshots = null;
    if (cli.hasOption(CommonOptions.OPT_ALL)) {
      snapshots = RepoUtil.getSnapshotByName(targetWs.getItemId().getUuidValue(), "", false, Integer.MAX_VALUE, repo, config);
    } else {
      List<IScmCommandLineArgument> ssSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_SNAPSHOTS.getId()), config);
      SubcommandUtil.validateArgument(ssSelectors, RepoUtil.ItemType.SNAPSHOT);
      
      List<String> selectors = RepoUtil.getSelectors(ssSelectors);
      snapshots = RepoUtil.getSnapShots(targetWs.getItemId().getUuidValue(), selectors, repo, config);
    }
    
    ParmsRemoveBaselineSet removeParms = new ParmsRemoveBaselineSet();
    baselineSet = new ParmsBaselineSet();
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    List<IBaselineSet> deletedSnapshots = new ArrayList();
    
    for (IBaselineSet snapshot : snapshots) {
      try {
        workspace = new ParmsWorkspace(repo.getRepositoryURI(), targetWs.getItemId().getUuidValue());
        baselineSet.repositoryUrl = repo.getRepositoryURI();
        baselineSet.baselineSetItemId = snapshot.getItemId().getUuidValue();
        

        client.postRemoveBaselineSet(removeParms, null);
        
        deletedSnapshots.add(snapshot);
      }
      catch (TeamRepositoryException e) {
        printDeletedSnapshots(deletedSnapshots, repo, out);
        
        throw StatusHelper.wrap(NLS.bind(Messages.DeleteSnapshotCmd_FAILED, 
          AliasUtil.selector(snapshot.getName(), snapshot.getItemId(), 
          repo.getRepositoryURI(), RepoUtil.ItemType.SNAPSHOT), new Object[0]), 
          e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
      }
    }
    
    if (cli.hasOption(CommonOptions.OPT_JSON)) {
      printJsonizedDeletedSnapshots(deletedSnapshots, repo);
    }
    else if (cli.hasOption(CommonOptions.OPT_VERBOSE)) {
      printDeletedSnapshots(deletedSnapshots, repo, out);
    }
    
    if (!cli.hasOption(CommonOptions.OPT_JSON)) {
      if (deletedSnapshots.size() > 1) {
        out.println(Messages.DeleteSnapshotCmd_SUCCESS2);
      } else {
        out.println(Messages.DeleteSnapshotCmd_SUCCESS);
      }
    }
  }
  
  private void printJsonizedDeletedSnapshots(List<IBaselineSet> deletedSnapshots, ITeamRepository repo)
  {
    JSONArray jSnapshots = new JSONArray();
    
    for (IBaselineSet snapshot : deletedSnapshots) {
      JSONObject jSnapshot = JSONPrintUtil.jsonize(snapshot.getName(), 
        snapshot.getItemId().getUuidValue(), repo.getRepositoryURI());
      
      jSnapshots.add(jSnapshot);
    }
    
    config.getContext().stdout().print(jSnapshots);
  }
  
  public void printDeletedSnapshots(List<IBaselineSet> deletedSnapshots, ITeamRepository repo, IndentingPrintStream out)
  {
    if (deletedSnapshots.size() == 0) {
      return;
    }
    
    out.println(Messages.DeleteSnapshotCmd_DELETED_SNAPSHOTS);
    for (IBaselineSet snapshot : deletedSnapshots) {
      out.indent().println(AliasUtil.selector(snapshot.getName(), snapshot.getItemId(), 
        repo.getRepositoryURI(), RepoUtil.ItemType.SNAPSHOT));
    }
  }
}
