package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
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
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceTarget;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;

public class FlowWorkspaceTargetCmd
  extends AbstractSubcommand
{
  ParmsWorkspace ws;
  ParmsWorkspace targetWs;
  
  public FlowWorkspaceTargetCmd() {}
  
  protected void init(IScmClientConfiguration config, IFilesystemRestClient client)
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    

    IScmCommandLineArgument srcSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(srcSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, srcSelector);
    IWorkspace wsFound = RepoUtil.getWorkspace(srcSelector.getItemSelector(), true, true, repo, config);
    ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    

    IScmCommandLineArgument targetCmdArg = ScmCommandLineArgument.create(cli.getOptionValue(FlowWorkspaceTargetCmdOptions.OPT_TARGET), config);
    SubcommandUtil.validateArgument(targetCmdArg, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    repo = RepoUtil.loginUrlArgAncestor(config, client, targetCmdArg);
    wsFound = RepoUtil.getWorkspace(targetCmdArg.getItemSelector(), true, true, repo, config);
    targetWs = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
  }
  
  public void run() throws FileSystemException {
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    init(config, client);
    setWorkspaceFlowTarget(ws, targetWs, client, config);
  }
  
  public static void setWorkspaceFlowTarget(ParmsWorkspace ws, ParmsWorkspace targetWs, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    if (config.isDryRun()) {
      return;
    }
    

    ParmsWorkspaceTarget parms = new ParmsWorkspaceTarget();
    activeWorkspace = ws;
    targetWorkspace = targetWs;
    try
    {
      client.postSetWorkspaceTarget(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.FlowTargetCmd_CHANGE_FAILED, e, new IndentingPrintStream(config.getContext().stderr()), targetWorkspace.repositoryUrl);
    }
    
    config.getContext().stdout().println(Messages.FlowTargetCmd_TARGET_SUCCESFULLY_CHANGED);
  }
}
