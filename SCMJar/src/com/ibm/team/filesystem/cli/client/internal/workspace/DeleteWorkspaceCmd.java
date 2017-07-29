package com.ibm.team.filesystem.cli.client.internal.workspace;

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
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeleteWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
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
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;



public class DeleteWorkspaceCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public DeleteWorkspaceCmd() {}
  
  public static final NamedOptionDefinition OPT_PEER_WORKSPACE = new NamedOptionDefinition("b", "backup-workspace", 1);
  protected boolean peerWkspRequired = true;
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    if (peerWkspRequired) {
      options.addOption(OPT_PEER_WORKSPACE, Messages.DeleteWorkspaceCmdOptions_PEER_WS_HELP);
    }
    options.addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, getItemName(), 1, 1, "@"), getItemHelp());
    
    return options;
  }
  
  public String getItemHelp() {
    return Messages.DeleteWorkspaceCmdOptions_WS_HELP;
  }
  
  public String getItemName() {
    return "workspace";
  }
  
  public void run() throws FileSystemException {
    deleteWorkspace(true, true, config, Messages.DeleteWorkspaceCmd_SUCCESS, Messages.DeleteWorkspaceCmd_FAILURE);
  }
  
  public static void deleteWorkspace(boolean delWorkspace, boolean delStream, IScmClientConfiguration config, String successMessage, String failureMessage)
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    IScmCommandLineArgument peerWsSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_PEER_WORKSPACE, null), config);
    SubcommandUtil.validateArgument(peerWsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    if ((peerWsSelector != null) && (peerWsSelector.getRepositorySelector() != null) && 
      (!RepoUtil.isRepoUriSame(wsSelector.getRepositorySelector(), peerWsSelector.getRepositorySelector(), config))) {
      throw StatusHelper.argSyntax(Messages.DeleteWorkspaceCmd_MUST_HAVE_SAME_REPO);
    }
    

    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
    
    IWorkspace ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), delWorkspace, delStream, repo, config);
    if ((!ws.isStream()) && (cli.hasOption(OPT_PEER_WORKSPACE))) {
      throw StatusHelper.argSyntax(Messages.DeleteWorkspaceCmd_PEER_WS_FOR_STREAM);
    }
    
    ParmsDeleteWorkspace parms = new ParmsDeleteWorkspace();
    workspace = new ParmsWorkspace(repo.getRepositoryURI(), ws.getItemId().getUuidValue());
    
    if (cli.hasOption(OPT_PEER_WORKSPACE)) {
      IWorkspace peerWs = RepoUtil.getWorkspace(peerWsSelector.getItemSelector(), true, true, repo, config);
      peerWorkspace = new ParmsWorkspace(repo.getRepositoryURI(), peerWs.getItemId().getUuidValue());
    }
    
    try
    {
      client.postDeleteWorkspace(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(failureMessage, e, new IndentingPrintStream(config.getContext().stderr()), workspace.repositoryUrl);
    }
    
    config.getContext().stdout().println(successMessage);
  }
}
