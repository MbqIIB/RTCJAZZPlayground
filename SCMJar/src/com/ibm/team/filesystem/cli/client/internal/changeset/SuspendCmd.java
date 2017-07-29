package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptResultDisplayer;
import com.ibm.team.filesystem.cli.core.internal.ScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.ITypedPreferenceRegistry;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBackupDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeletedContentDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsOutOfSyncInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSuspendChangeSets;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.SuspendResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.SandboxUpdateDilemmaDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLineArgument;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.common.DiscardNotInHistoryException;
import com.ibm.team.scm.common.GapException;
import com.ibm.team.scm.common.NWayConflictUnsupportedException;
import com.ibm.team.scm.common.PatchInProgressException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.osgi.util.NLS;








public class SuspendCmd
  extends AbstractSubcommand
{
  public SuspendCmd() {}
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_WORKSPACE_NAME.getId(), null), config);
    SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.WORKSPACE);
    List<IScmCommandLineArgument> csSelectors = new ArrayList(cli.getOptionValues(CommonOptions.OPT_CHANGESET_SELECTORS).size());
    for (ICommandLineArgument csArg : cli.getOptionValues(CommonOptions.OPT_CHANGESET_SELECTORS)) {
      csSelectors.add(ScmCommandLineArgument.create(csArg, config));
    }
    SubcommandUtil.validateArgument(csSelectors, RepoUtil.ItemType.CHANGESET);
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    Map<ParmsWorkspace, ChangeSetSyncDTO[]> wsToCsMap = findSuspendChangesets(csSelectors, client, wsSelector, config, out);
    suspendChangesets(wsToCsMap, client, config, out);
    if (!config.isJSONEnabled()) {
      out.println(Messages.SuspendCmd_CHANGESETS_SUCCESSFULLY_SUSPENDED);
    }
  }
  
  private Map<ParmsWorkspace, ChangeSetSyncDTO[]> findSuspendChangesets(List<IScmCommandLineArgument> csSelectors, IFilesystemRestClient client, IScmCommandLineArgument wsSelector, IScmClientConfiguration config, IndentingPrintStream out) throws FileSystemException
  {
    Map<ParmsWorkspace, ChangeSetSyncDTO[]> wsToCsMap = new HashMap();
    List csSelectorList = new ArrayList();
    ParmsWorkspace ws = null;
    ITeamRepository repo = null;
    
    if (wsSelector != null) {
      ws = RepoUtil.findWorkspaceAndLogin(wsSelector, client, config);
      repo = RepoUtil.getSharedRepository(repositoryUrl, true);
    }
    else {
      repo = RepoUtil.loginUrlArgAncestor(config, client, null);
      ws = RepoUtil.findWorkspaceInSandbox(null, repo.getId(), client, config);
    }
    

    RepoUtil.validateItemRepos(RepoUtil.ItemType.CHANGESET, csSelectors, repo, config);
    

    for (IScmCommandLineArgument csSelector : csSelectors) {
      IUuidAliasRegistry.IUuidAlias csUuidAlias = RepoUtil.lookupUuidAndAlias(csSelector.getItemSelector());
      if (csUuidAlias == null) {
        printError(csSelector, out);
      }
      
      csSelectorList.add(csSelector.getItemSelector());
    }
    
    wsToCsMap.put(ws, RepoUtil.findChangeSets(csSelectorList, false, workspaceItemId, "workspace", repo.getRepositoryURI(), client, config));
    return wsToCsMap;
  }
  
  private void printError(IScmCommandLineArgument csSelector, IndentingPrintStream out) throws FileSystemException {
    out.println(Messages.Common_CS_NOT_FOUND_HEADER);
    out.indent().println(csSelector.getItemSelector());
    out.println(Messages.SuspendCmd_HINT);
    throw StatusHelper.itemNotFound(Messages.Common_CS_NOT_FOUND);
  }
  
  private void suspendChangesets(Map<ParmsWorkspace, ChangeSetSyncDTO[]> wsToCsMap, IFilesystemRestClient client, IScmClientConfiguration config, IndentingPrintStream out) throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    

    boolean printHeader = true;
    for (Map.Entry<ParmsWorkspace, ChangeSetSyncDTO[]> entry : wsToCsMap.entrySet()) {
      ParmsSuspendChangeSets parmsCs = new ParmsSuspendChangeSets();
      workspace = ((ParmsWorkspace)entry.getKey());
      
      ChangeSetSyncDTO[] csDTOList = (ChangeSetSyncDTO[])entry.getValue();
      changeSetItemIds = new String[csDTOList.length];
      for (int i = 0; i < csDTOList.length; i++) {
        changeSetItemIds[i] = csDTOList[i].getChangeSetItemId();
      }
      
      preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
      
      pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
      if (cli.hasOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED)) {
        pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
      } else {
        pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
      }
      
      outOfSyncInstructions = new ParmsOutOfSyncInstructions();
      outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
      outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
      
      sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
      sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
      sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
      
      sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
      sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
      
      if (!cli.hasOption(CommonOptions.OPT_RELEASE_LOCK)) {
        autoReleaseLocks = Boolean.valueOf(((ScmClientConfiguration)config).getPersistentPreferences().getReleaseLockForSuspend());
      }
      else {
        autoReleaseLocks = Boolean.valueOf(Boolean.parseBoolean(cli.getOption(CommonOptions.OPT_RELEASE_LOCK).toString()));
      }
      
      try
      {
        result = client.postSuspendChangeSets(parmsCs, null);
      } catch (TeamRepositoryException e) {
        SuspendResultDTO result;
        PatchInProgressException port = (PatchInProgressException)SubcommandUtil.findExceptionByType(PatchInProgressException.class, e);
        if (port != null) {
          throw StatusHelper.portsInProgress(port.getLocalizedMessage());
        }
        
        GapException gap = (GapException)SubcommandUtil.findExceptionByType(GapException.class, e);
        if (gap != null) {
          throw StatusHelper.gap(Messages.SuspendCmd_9 + " " + Messages.DeliverCmd_HINT_ON_GAP);
        }
        
        NWayConflictUnsupportedException nway = (NWayConflictUnsupportedException)SubcommandUtil.findExceptionByType(NWayConflictUnsupportedException.class, e);
        if (nway != null) {
          throw StatusHelper.nWayConflict(Messages.SuspendCmd_10);
        }
        
        DiscardNotInHistoryException notinHistory = (DiscardNotInHistoryException)SubcommandUtil.findExceptionByType(DiscardNotInHistoryException.class, e);
        if (notinHistory != null) {
          throw StatusHelper.discardNotInHistory(Messages.SuspendCmd_CS_NOT_IN_HISTORY);
        }
        
        throw StatusHelper.wrap(Messages.SuspendCmd_11, e, new IndentingPrintStream(config.getContext().stderr()));
      }
      SuspendResultDTO result;
      if (result.isCancelled()) {
        if (result.getOutOfSyncShares().size() > 0) {
          AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
        }
        
        int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChanges());
        if (noOfUncheckedInChanges > 0) {
          throw StatusHelper.uncheckedInChanges(NLS.bind(Messages.AcceptCmd2_UNCHECKEDIN_ITEMS_PRESENT, Integer.valueOf(noOfUncheckedInChanges), CommonOptions.OPT_OVERWRITE_UNCOMMITTED.getName()));
        }
      }
      

      if (cli.hasOption(CommonOptions.OPT_VERBOSE)) {
        if ((printHeader) && (!config.isJSONEnabled())) {
          out.println(Messages.SuspendCmd_7);
          printHeader = false;
        }
        
        printSuspendedChangesets((ParmsWorkspace)entry.getKey(), csDTOList, client, config, out);
      }
      

      if (result.getSandboxUpdateDilemma().getBackedUpToShed().size() > 0) {
        SubcommandUtil.showShedUpdate(Messages.AcceptResultDisplayer_SHED_MESSAGE, out, result.getSandboxUpdateDilemma().getBackedUpToShed());
      }
      
      if (result.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0) {
        SubcommandUtil.showDeletedContent(result.getSandboxUpdateDilemma().getDeletedContentShareables(), out);
      }
    }
  }
  
  private void printSuspendedChangesets(ParmsWorkspace ws, ChangeSetSyncDTO[] csDTOList, IFilesystemRestClient client, IScmClientConfiguration config, IndentingPrintStream out) throws FileSystemException
  {
    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    options.setVerbose(true);
    options.enablePrinter(0);
    options.enablePrinter(1);
    options.enablePrinter(10);
    options.enablePrinter(4);
    options.enablePrinter(6);
    options.enableFilter(0);
    options.enableFilter(11);
    options.enableFilter(24);
    options.enableFilter(25);
    options.enableFilter(1);
    options.enableFilter(4);
    
    int maxChanges = CommonOptions.getMaxChangesToInterpret(config);
    options.setMaxChanges(maxChanges);
    
    options.addFilter(UUID.valueOf(workspaceItemId), 0);
    for (ChangeSetSyncDTO cs : csDTOList) {
      options.addFilter(UUID.valueOf(cs.getChangeSetItemId()), 4);
      
      if (!options.isInFilter(UUID.valueOf(cs.getComponentItemId()), 1)) {
        options.addFilter(UUID.valueOf(cs.getComponentItemId()), 1);
      }
    }
    
    IWorkspaceConnection wsConn = null;
    try {
      wsConn = ws.getWorkspaceConnection(null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.DiffCmd_84, e, new IndentingPrintStream(config.getContext().stderr()));
    }
    PendingChangesUtil.printPendingChanges(client, Collections.singletonList(wsConn), options, out.indent(), config);
  }
}
