package com.ibm.team.filesystem.cli.client.internal.changeset;

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
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.workitem.common.model.IWorkItem;
import java.util.Collections;
import org.eclipse.osgi.util.NLS;

















public abstract class ChangesetWorkitemBase
  extends AbstractSubcommand
{
  public IWorkItem workItem;
  public String wsId = null;
  
  public ChangeSetSyncDTO csDTO;
  public String repoUri;
  public String wiRepoUri;
  
  public ChangesetWorkitemBase() {}
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    

    IScmCommandLineArgument csSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_CHANGESET.getId()), config);
    SubcommandUtil.validateArgument(csSelector, RepoUtil.ItemType.CHANGESET);
    
    ITeamRepository wiRepo = null;
    

    String wi = null;
    
    IScmCommandLineArgument wiSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetAssociateWorkitemOptions.OPT_WORKITEM.getId()), config, false);
    wiRepoUri = wiSelector.getRepositorySelector();
    wi = wiSelector.getItemSelector();
    
    try
    {
      wiNumber = Integer.parseInt(wi);
    } catch (NumberFormatException localNumberFormatException) { int wiNumber;
      throw StatusHelper.argSyntax(NLS.bind(Messages.ChangesetAssociateWorkitemCmd_2, wi));
    }
    
    int wiNumber;
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_WORKSPACE_NAME.getId(), null), config);
    SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.WORKSPACE);
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ParmsWorkspace ws = null;
    
    ITeamRepository repo;
    if (wsSelector != null) {
      ws = RepoUtil.findWorkspaceAndLogin(wsSelector, client, config);
      ITeamRepository repo = RepoUtil.getSharedRepository(repositoryUrl, true);
      

      RepoUtil.validateItemRepos(RepoUtil.ItemType.CHANGESET, Collections.singletonList(csSelector), repo, config);
      
      wsId = workspaceItemId;
      repoUri = repositoryUrl;
    }
    else {
      repo = RepoUtil.loginUrlArgAncestor(config, client, csSelector);
      repoUri = repo.getRepositoryURI();
    }
    

    csDTO = RepoUtil.findChangeSet(csSelector.getItemSelector(), false, wsId, "workspace", 
      repoUri, client, config);
    

    if (wiRepoUri == null) {
      workItem = RepoUtil.findWorkItem(wiNumber, repo, config);
    }
    else {
      wiRepo = RepoUtil.login(config, client, config.getConnectionInfo(wiRepoUri));
      workItem = RepoUtil.findWorkItem(wiNumber, wiRepo, config);
      wiRepoUri = wiRepo.getRepositoryURI();
    }
    

    doIt();
  }
  
  protected abstract void doIt()
    throws FileSystemException;
}
