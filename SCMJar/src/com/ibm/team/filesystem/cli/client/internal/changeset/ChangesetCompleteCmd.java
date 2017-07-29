package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.portcommand.CurrentPortCmd;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCloseChangeSetsDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCompleteChangeSets;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.CompleteChangeSetsResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ChangeSetDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.CompletedChangeSetException;
import com.ibm.team.scm.common.IChangeSet;
import com.ibm.team.scm.common.VersionablePermissionDeniedException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.osgi.util.NLS;




public class ChangesetCompleteCmd
  extends AbstractSubcommand
{
  public ChangesetCompleteCmd() {}
  
  public void run()
    throws FileSystemException
  {
    setComplete(config);
  }
  
  public static void setComplete(IScmClientConfiguration config) throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_WORKSPACE_NAME.getId(), null), config);
    SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.WORKSPACE);
    List<IScmCommandLineArgument> csSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(ChangesetCommonOptions.OPT_CHANGESETS.getId()), config);
    SubcommandUtil.validateArgument(csSelectors, RepoUtil.ItemType.CHANGESET);
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = null;
    ParmsWorkspace ws = null;
    ITeamRepository repoCs;
    if (wsSelector != null) {
      ws = RepoUtil.findWorkspaceAndLogin(wsSelector, client, config);
      repo = RepoUtil.getSharedRepository(repositoryUrl, true);
      

      RepoUtil.validateItemRepos(RepoUtil.ItemType.CHANGESET, csSelectors, repo, config);
    } else {
      for (IScmCommandLineArgument csSelector : csSelectors) {
        repoCs = RepoUtil.loginUrlArgAncestor(config, client, csSelector);
        if (repo == null) {
          repo = repoCs;
        } else if (!repo.getId().equals(repoCs.getId())) {
          throw StatusHelper.inappropriateArgument(Messages.ChangesetCompleteCmd_ALL_CS_NOT_IN_SAME_REPO);
        }
      }
      ws = RepoUtil.findWorkspaceInSandbox(null, repo.getId(), client, config);
    }
    

    List<String> csList = new ArrayList(csSelectors.size());
    for (IScmCommandLineArgument csSelector : csSelectors) {
      IChangeSet changeSet = RepoUtil.findChangeSet(csSelector.getItemSelector(), repo, config);
      if (!csList.contains(changeSet.getItemId().getUuidValue())) {
        csList.add(changeSet.getItemId().getUuidValue());
      }
    }
    

    setComplete(ws, csList, repo.getRepositoryURI(), client, config);
  }
  
  public static void setComplete(ParmsWorkspace ws, List<String> csList, String repoUri, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ParmsCompleteChangeSets parmsCompleteCS = new ParmsCompleteChangeSets();
    workspace = ws;
    changeSetItemIds = ((String[])csList.toArray(new String[csList.size()]));
    
    closeChangeSetsDilemmaHandler = new ParmsCloseChangeSetsDilemmaHandler();
    ICommandLine cli = config.getSubcommandCommandLine();
    if (cli.hasOption(ChangesetSetCmd.OPT_SKIP_MERGE_TARGET)) {
      closeChangeSetsDilemmaHandler.portsInProgressInstruction = "continue";
    } else {
      closeChangeSetsDilemmaHandler.portsInProgressInstruction = "cancel";
    }
    
    CompleteChangeSetsResultDTO result = null;
    try {
      result = client.postCompleteChangeSets(parmsCompleteCS, null);
    } catch (TeamRepositoryException e) {
      CompletedChangeSetException csNotComplete = (CompletedChangeSetException)SubcommandUtil.findExceptionByType(CompletedChangeSetException.class, e);
      if (csNotComplete != null) {
        throw StatusHelper.completeCSFailure(Messages.ChangesetCompleteCmd_9, csNotComplete);
      }
      
      VersionablePermissionDeniedException permissionDenied = (VersionablePermissionDeniedException)SubcommandUtil.findExceptionByType(
        VersionablePermissionDeniedException.class, e);
      if (permissionDenied != null) {
        String msg = permissionDenied.getLocalizedMessage();
        throw StatusHelper.permissionFailure(
          (msg != null) && (msg.length() > 0) ? msg : Messages.Common_VERSIONABLE_PERMISSSION_DENIED);
      }
      
      throw StatusHelper.wrap(Messages.ChangesetCompleteCmd_9, e, new IndentingPrintStream(
        config.getContext().stderr()), ws != null ? repositoryUrl : repoUri);
    }
    
    List<ChangeSetDTO> changesetDtoList = result.getTargetChangeSets();
    
    if (changesetDtoList != null) {
      int noOftargetChangeSets = changesetDtoList.size();
      boolean isPartialSuccess = csList.size() > noOftargetChangeSets;
      

      if (noOftargetChangeSets != 0)
      {

        if (result.isCancelled()) {
          throw StatusHelper.portsInProgress(NLS.bind(Messages.ListUsersCmd_USE_OPTION, ChangesetSetCmd.OPT_SKIP_MERGE_TARGET.getName()));
        }
        ISubcommandDefinition defnTemp = SubcommandUtil.getClassSubCommandDefn(config, 
          CurrentPortCmd.class);
        if (isPartialSuccess) {
          config.getContext().stdout().println(Messages.ChangesetCompleteCmd_10);
          printUnCompletedChangeSetList(changesetDtoList, config, RepoUtil.getSharedRepository(repositoryUrl, true));
          config.getContext().stdout().println(NLS.bind(Messages.ChangeSetPortCmd_PORT_TARGETS_PRESENT_Hint, new String[] {
            config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp).toString() }));
          

          throw StatusHelper.returnValue(54);
        }
        
        printUnCompletedChangeSetList(changesetDtoList, config, RepoUtil.getSharedRepository(repositoryUrl, true));
        config.getContext().stdout().println(NLS.bind(Messages.ChangeSetPortCmd_PORT_TARGETS_PRESENT_Hint, new String[] {
          config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp).toString() }));
        

        throw StatusHelper.returnValue(37);
      }
    }
  }
  


  public static void printUnCompletedChangeSetList(List<ChangeSetDTO> changesetDtoList, IScmClientConfiguration config, ITeamRepository repo)
  {
    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
    err.println(Messages.ChangeSetPortCmd_PORT_TARGETS_PRESENT);
    for (ChangeSetDTO csList : changesetDtoList) {
      err.indent().println(RepoUtil.lookupUuidAndAlias(csList.getItemId(), repo.getRepositoryURI()).getMonicker());
    }
  }
}
