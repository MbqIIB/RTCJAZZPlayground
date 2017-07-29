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
import com.ibm.team.filesystem.client.rest.parameters.ParmsSetCurrentChangeSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IChangeSet;
import com.ibm.team.scm.common.IWorkspace;
import java.util.Collections;


public class ChangesetCurrentCmd
  extends AbstractSubcommand
{
  public ChangesetCurrentCmd() {}
  
  public void run()
    throws FileSystemException
  {
    setCurrent(config);
  }
  
  public static void setCurrent(IScmClientConfiguration config) throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_WORKSPACE_NAME.getId(), null), config);
    SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.WORKSPACE);
    IScmCommandLineArgument csSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_CHANGESET.getId()), config);
    SubcommandUtil.validateArgument(csSelector, RepoUtil.ItemType.CHANGESET);
    
    setCurrent(wsSelector, csSelector, config);
  }
  
  public static void setCurrent(IScmCommandLineArgument wsSelector, IScmCommandLineArgument csSelector, IScmClientConfiguration config)
    throws FileSystemException
  {
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = null;
    ParmsWorkspace ws = null;
    
    if (wsSelector != null) {
      repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
      

      RepoUtil.validateItemRepos(RepoUtil.ItemType.CHANGESET, Collections.singletonList(csSelector), repo, config);
      
      ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    } else {
      repo = RepoUtil.loginUrlArgAncestor(config, client, csSelector);
      ws = RepoUtil.findWorkspaceInSandbox(null, repo.getId(), client, config);
    }
    
    IChangeSet changeSet = RepoUtil.findChangeSet(csSelector.getItemSelector(), repo, config);
    

    ParmsSetCurrentChangeSet parms = new ParmsSetCurrentChangeSet();
    changeSetItemId = changeSet.getItemId().getUuidValue();
    workspace = ws;
    try
    {
      client.postSetCurrentChangeSet(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.ChangesetCurrentCmd_UNABLE_TO_SET_CURRENT, e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
  }
}
