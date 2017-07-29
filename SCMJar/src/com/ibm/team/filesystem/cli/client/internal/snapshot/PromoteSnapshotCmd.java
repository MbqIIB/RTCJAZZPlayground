package com.ibm.team.filesystem.cli.client.internal.snapshot;

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
import com.ibm.team.filesystem.client.rest.parameters.ParmsUpdateBaselineSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IWorkspace;
import java.util.List;





public class PromoteSnapshotCmd
  extends AbstractSubcommand
{
  public PromoteSnapshotCmd() {}
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    

    IScmCommandLineArgument target = ScmCommandLineArgument.create(cli.getOptionValue(PromoteSnapshotCmdOpts.OPT_TARGET.getId()), config);
    SubcommandUtil.validateArgument(target, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, target);
    
    IWorkspace targetWs = RepoUtil.getWorkspace(target.getItemSelector(), true, true, repo, config);
    

    List<IScmCommandLineArgument> ssSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(PromoteSnapshotCmdOpts.OPT_SNAPSHOTS.getId()), config);
    SubcommandUtil.validateArgument(ssSelectors, RepoUtil.ItemType.SNAPSHOT);
    RepoUtil.validateItemRepos(RepoUtil.ItemType.SNAPSHOT, ssSelectors, repo, config);
    
    List<String> selectors = RepoUtil.getSelectors(ssSelectors);
    List<IBaselineSet> snapshots = RepoUtil.getSnapShots(null, selectors, repo, config);
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    ParmsUpdateBaselineSet updateParms = new ParmsUpdateBaselineSet();
    baselineSet = new ParmsBaselineSet();
    
    int idx = 0;
    for (IBaselineSet snapshot : snapshots) {
      try {
        promotionWorkspace = new ParmsWorkspace(repo.getRepositoryURI(), targetWs.getItemId().getUuidValue());
        baselineSet.repositoryUrl = repo.getRepositoryURI();
        baselineSet.baselineSetItemId = snapshot.getItemId().getUuidValue();
        

        client.postUpdateBaselineSet(updateParms, null);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.PromoteSnapshotCmd_PROMOTION_FAILED, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
      }
      
      if (idx == 0) {
        out.println(Messages.PromoteSnapshotCmd_PROMOTED_SNAPSHOT);
      }
      
      out.println("  " + (String)selectors.get(idx++));
    }
  }
}
