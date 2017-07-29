package com.ibm.team.filesystem.cli.client.internal.createcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptResultDisplayer;
import com.ibm.team.filesystem.cli.client.internal.subcommands.ConflictsCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.ResolveCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.StatusCmd;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.ChangeSetStateFactory;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBackupDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCreateBaselineSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCreateBaselineSetDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsGetBaselines;
import com.ibm.team.filesystem.client.rest.parameters.ParmsOutOfSyncInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.ProblemChangeSetsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.BaselineDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.BaselineSetDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ConfigurationDescriptorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.GetBaselinesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.workspace.CreateBaselineSetResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.PermissionDeniedException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.repository.common.util.NLS;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IWorkspace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


public class CreateSnapshotCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public CreateSnapshotCmd() {}
  
  public static final IOptionKey OPT_DESC = new OptionKey("desc");
  public static final IOptionKey OPT_NAME = new OptionKey("name");
  public static final NamedOptionDefinition OPT_FORCE_BASELINE_CREATION = new NamedOptionDefinition("f", "forceBaselineCreation", 0);
  public static final NamedOptionDefinition OPT_FAILED_ON_OPEN_CHANGE_SETS = new NamedOptionDefinition("a", "failOnActiveCS", 0);
  public static final NamedOptionDefinition OPT_ALLOW_PARTIAL_HIERARCHY = new NamedOptionDefinition("p", "allowPartialHierarchy", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(OPT_DESC, "d", "description", Messages.CreateSnapshotCmd_Description, 1)
      .addOption(CommonOptions.OPT_COMPS_SELECTOR, Messages.CreateSnapshotCmd_ExcludeComponents)
      .addOption(OPT_FAILED_ON_OPEN_CHANGE_SETS, Messages.CreateSnapshotCmd_OPEN_CHANGE_SETS_HELP, false)
      .addOption(OPT_ALLOW_PARTIAL_HIERARCHY, Messages.CreateSnapshotCmd_OPT_ALLOW_PARTIAL_HIERARCHY_HELP, false)
      .addOption(OPT_FORCE_BASELINE_CREATION, Messages.CreateSnapshotCmd_ForceBaselineCreation)
      .addOption(OPT_NAME, "n", "name", Messages.CreateSnapshotCmd_Name, 1)
      .addOption(CommonOptions.OPT_POSITIONAL_ARG_SEPARATOR, NLS.bind(
      Messages.PositionalArgSeparator_Help, CommonOptions.OPT_WORKSPACE.getName(), new Object[0]))
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@"), Messages.CreateSnapshotCmd_Workspace);
    return options;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    

    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
    
    IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    ParmsWorkspace ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    

    WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
    

    List<String> componentsToExclude = new ArrayList();
    WorkspaceComponentDTO compDTO; if (cli.hasOption(CommonOptions.OPT_COMPS_SELECTOR)) {
      List<IScmCommandLineArgument> compSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(CommonOptions.OPT_COMPS_SELECTOR), config);
      SubcommandUtil.validateArgument(compSelectors, RepoUtil.ItemType.COMPONENT);
      List<String> comps = RepoUtil.getSelectors(compSelectors);
      List<String> compNotFound = new ArrayList();
      boolean isComponentFound;
      for (String comp : comps) {
        isComponentFound = false;
        IUuidAliasRegistry.IUuidAlias compAlias = RepoUtil.lookupUuidAndAlias(comp);
        if (!compNotFound.contains(comp))
        {

          for (Iterator localIterator2 = wsDetails.getComponents().iterator(); localIterator2.hasNext();) { compDTO = (WorkspaceComponentDTO)localIterator2.next();
            if (((compAlias != null) && (compAlias.getUuid().getUuidValue().equals(compDTO.getItemId()))) || 
              (comp.equals(compDTO.getName()))) {
              if (!componentsToExclude.contains(compDTO.getItemId()))
              {

                componentsToExclude.add(compDTO.getItemId());
              }
              isComponentFound = true;
              break;
            }
          }
          if (!isComponentFound)
            compNotFound.add(comp);
        }
      }
      if (compNotFound.size() != 0) {
        IndentingPrintStream stream = new IndentingPrintStream(config.getContext().stderr());
        stream.println(NLS.bind(Messages.CreateSnapshotCmd_ERROR_WRONG_COMPONENT, wsDetails.getName(), new Object[0]));
        for (String comp : compNotFound) {
          stream.indent().println(comp);
        }
        throw StatusHelper.itemNotFound(Messages.CreateSnapshotCmd_ERROR_MSG_WRONG_COMPONENT);
      }
    }
    

    ParmsCreateBaselineSet parms = new ParmsCreateBaselineSet();
    workspace = ws;
    excludedComponentItemIds = ((String[])componentsToExclude.toArray(new String[componentsToExclude.size()]));
    createNewBaselines = Boolean.valueOf(true);
    
    name = wsDetails.getName();
    if (cli.hasOption(OPT_NAME)) {
      name = cli.getOption(OPT_NAME);
    }
    
    comment = "";
    if (cli.hasOption(OPT_DESC)) {
      comment = cli.getOption(OPT_DESC);
    }
    
    forceBaselineCreation = Boolean.valueOf(false);
    if (cli.hasOption(OPT_FORCE_BASELINE_CREATION)) {
      forceBaselineCreation = Boolean.valueOf(true);
    }
    
    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    
    createBaselineSetDilemmaHandler = new ParmsCreateBaselineSetDilemmaHandler();
    createBaselineSetDilemmaHandler.conflictedConfigurationsDirection = "cancel";
    createBaselineSetDilemmaHandler.conflictedConfigurationsInSubcomponentsDirection = "cancel";
    createBaselineSetDilemmaHandler.inaccessibleConfigurationsInSubcomponentsDirection = "continue";
    createBaselineSetDilemmaHandler.notFoundConfigurationsInSubcomponentsDirection = "continue";
    if (cli.hasOption(OPT_ALLOW_PARTIAL_HIERARCHY)) {
      createBaselineSetDilemmaHandler.entireHierarchyNotInSnapshotDirection = "continue";
    } else {
      createBaselineSetDilemmaHandler.entireHierarchyNotInSnapshotDirection = "cancel";
    }
    
    if (cli.hasOption(OPT_FAILED_ON_OPEN_CHANGE_SETS))
    {
      createBaselineSetDilemmaHandler.activeChangeSetsInSubcomponentsDirection = "cancel";
      createBaselineSetDilemmaHandler.activeChangeSetsInConfigurationsDirection = "cancel";
    }
    
    outOfSyncInstructions = new ParmsOutOfSyncInstructions();
    outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
    outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
    
    sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
    
    CreateBaselineSetResultDTO result = null;
    try {
      result = client.postCreateBaselineSet(parms, null);
    } catch (TeamRepositoryException e) {
      if ((e instanceof PermissionDeniedException)) {
        String message = e.getMessage();
        if ((e.getData() instanceof IComponentHandle)) {
          IComponentHandle inaccessibleComponent = (IComponentHandle)e.getData();
          message = wsFound.isStream() ? 
            Messages.CreateSnapshotCmd_ERROR_COMPONENT_PERMISSION_STREAM : 
            Messages.CreateSnapshotCmd_ERROR_COMPONENT_PERMISSION_REPOSITORY_WORKSPACE;
          message = NLS.bind(message, inaccessibleComponent.getItemId().getUuidValue(), new Object[0]);
        }
        throw StatusHelper.permissionFailure(message);
      }
      throw StatusHelper.wrap(Messages.CreateSnapshotCmd_CouldNotCreateSnapshotTRE, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
    
    Object changeSets;
    if (result.isCancelled()) {
      if (result.getOutOfSyncShares().size() > 0) {
        AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
      }
      

      int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result
        .getConfigurationsWithUncheckedInChanges());
      
      noOfUncheckedInChanges = noOfUncheckedInChanges + SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChangesInSubcomponents());
      if (noOfUncheckedInChanges > 0) {
        throw StatusHelper.uncheckedInChanges(NLS.bind(
          Messages.AcceptCmd2_UNCHECKEDIN_ITEMS_PRESENT, Integer.valueOf(noOfUncheckedInChanges), new Object[] {
          CommonOptions.OPT_OVERWRITE_UNCOMMITTED.getName() }));
      }
      
      int noOfConflicts = result.getConfigurationsWithConflicts().size();
      if (noOfConflicts > 0) {
        ISubcommandDefinition defnTemp1 = SubcommandUtil.getClassSubCommandDefn(config, ResolveCmd.class);
        ISubcommandDefinition defnTemp2 = SubcommandUtil.getClassSubCommandDefn(config, ConflictsCmd.class);
        ISubcommandDefinition defnTemp3 = SubcommandUtil.getClassSubCommandDefn(config, StatusCmd.class);
        
        throw 
          StatusHelper.conflict(Messages.CreateSnapshotCmd_CouldNotCreateSnapshotConflict + 
          " " + NLS.bind(Messages.AcceptCmd_CONFLICT_GUIDANCE, 
          new String[] {
          config.getContext().getAppName(), 
          SubcommandUtil.getExecutionString(defnTemp1).toString(), 
          SubcommandUtil.getExecutionString(defnTemp2).toString(), 
          SubcommandUtil.getExecutionString(defnTemp3).toString() }, new Object[0]));
      }
      
      noOfConflicts = result.getConfigurationsWithConflictsInSubcomponents().size();
      if (noOfConflicts > 0) {
        ISubcommandDefinition defnTemp1 = SubcommandUtil.getClassSubCommandDefn(config, ResolveCmd.class);
        ISubcommandDefinition defnTemp2 = SubcommandUtil.getClassSubCommandDefn(config, ConflictsCmd.class);
        ISubcommandDefinition defnTemp3 = SubcommandUtil.getClassSubCommandDefn(config, StatusCmd.class);
        
        throw 
          StatusHelper.conflict(Messages.CreateSnapshotCmd_CouldNotCreateSnapshotConflict_subcomponents + 
          " " + NLS.bind(Messages.AcceptCmd_CONFLICT_GUIDANCE, 
          new String[] {
          config.getContext().getAppName(), 
          SubcommandUtil.getExecutionString(defnTemp1).toString(), 
          SubcommandUtil.getExecutionString(defnTemp2).toString(), 
          SubcommandUtil.getExecutionString(defnTemp3).toString() }, new Object[0]));
      }
      

      Object activeChangeSets = result.getActiveChangeSetsInSubcomponents();
      Iterator localIterator3; if (((List)activeChangeSets).size() != 0) {
        if (!config.isJSONEnabled()) {
          IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
          err.println(Messages.CreateSnapshotCmd_activeChangeSetsHeaderSubcomponents);
          for (compDTO = ((List)activeChangeSets).iterator(); compDTO.hasNext(); 
              









              localIterator3.hasNext())
          {
            ProblemChangeSetsDTO changeSet = (ProblemChangeSetsDTO)compDTO.next();
            Object changeSets = PendingChangesUtil.getChangeSets2(
              changeSet.getRepositoryURL(), 
              changeSet.getChangeSetItemIds(), 
              false, 
              client, 
              config);
            IndentingPrintStream indent = err.indent();
            PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
            options.enablePrinter(4);
            
            localIterator3 = ((List)changeSets).iterator(); continue;ChangeSetSyncDTO cs = (ChangeSetSyncDTO)localIterator3.next();
            JSONObject csObj = new JSONObject();
            JSONPrintUtil.jsonizeChangeSetHeader(csObj, cs, new ChangeSetStateFactory(), options, config);
            PendingChangesUtil.printChangeSetHeader(
              csObj, 
              changeSet.getRepositoryURL(), 
              null, 
              null, 
              null, 
              indent);
          }
        }
        

        throw StatusHelper.activeCSFailure(NLS.bind(Messages.CreateSnapshotCmd_ERROR_ACTIVE_CHANGESETS_SUBCOMPONENTS, OPT_FAILED_ON_OPEN_CHANGE_SETS.getShortOpt(), new Object[] { OPT_FAILED_ON_OPEN_CHANGE_SETS.getName() }));
      }
      
      activeChangeSets = result.getActiveChangeSetsInConfigurations();
      if (((List)activeChangeSets).size() != 0) {
        if (!config.isJSONEnabled()) {
          IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
          err.println(Messages.CreateSnapshotCmd_activeChangeSetsHeader);
          for (compDTO = ((List)activeChangeSets).iterator(); compDTO.hasNext(); 
              









              localIterator3.hasNext())
          {
            ProblemChangeSetsDTO changeSet = (ProblemChangeSetsDTO)compDTO.next();
            changeSets = PendingChangesUtil.getChangeSets2(
              changeSet.getRepositoryURL(), 
              changeSet.getChangeSetItemIds(), 
              false, 
              client, 
              config);
            IndentingPrintStream indent = err.indent();
            PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
            options.enablePrinter(4);
            
            localIterator3 = ((List)changeSets).iterator(); continue;ChangeSetSyncDTO cs = (ChangeSetSyncDTO)localIterator3.next();
            JSONObject csObj = new JSONObject();
            JSONPrintUtil.jsonizeChangeSetHeader(csObj, cs, new ChangeSetStateFactory(), options, config);
            PendingChangesUtil.printChangeSetHeader(
              csObj, 
              changeSet.getRepositoryURL(), 
              null, 
              null, 
              null, 
              indent);
          }
        }
        

        throw StatusHelper.activeCSFailure(NLS.bind(Messages.CreateSnapshotCmd_ERROR_ACTIVE_CHANGESETS, OPT_FAILED_ON_OPEN_CHANGE_SETS.getShortOpt(), new Object[] { OPT_FAILED_ON_OPEN_CHANGE_SETS.getName() }));
      }
      

      List<ConfigurationDescriptorDTO> entireHierarchyConfigs = result.getEntireHierarchyNotIncludedInSnapshot();
      if (entireHierarchyConfigs.size() != 0) {
        if (!config.isJSONEnabled()) {
          IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
          err.println(Messages.CreateBaselineCmd_ERROR_ENTIRE_HIERARCHY_COMPONENTS_HEADER);
          for (changeSets = entireHierarchyConfigs.iterator(); ((Iterator)changeSets).hasNext();) { ConfigurationDescriptorDTO descriptorDTO = (ConfigurationDescriptorDTO)((Iterator)changeSets).next();
            err.indent().println(descriptorDTO.getComponentItemId());
          }
        }
        throw StatusHelper.notEntireHierarchyInSnapshot(
          NLS.bind(Messages.CreateSnapshotCmd_ERROR_ENTIRE_HIERARCHY_COMPONENTS, OPT_ALLOW_PARTIAL_HIERARCHY.getShortOpt(), new Object[] { OPT_ALLOW_PARTIAL_HIERARCHY.getName() }));
      }
    } else {
      BaselineSetDTO baselineSet = result.getBaselineSet();
      JSONObject snapshot = JSONPrintUtil.jsonizeResult(Messages.CreateSnapshotCmd_SnapshotSuccessfullyCreated, baselineSet.getName(), baselineSet.getItemId(), repo.getRepositoryURI(), RepoUtil.ItemType.SNAPSHOT);
      

      ParmsGetBaselines parmsGetBaselines = new ParmsGetBaselines();
      repositoryUrl = repo.getRepositoryURI();
      baselineItemIds = ((String[])baselineSet.getBaselineItemIds().toArray(new String[baselineSet.getBaselineItemIds().size()]));
      
      GetBaselinesDTO resultGetBaselines = null;
      try {
        resultGetBaselines = client.getBaselines(parmsGetBaselines, null);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.Common_BASELINES_NOT_FOUND, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
      }
      
      JSONArray baselines = new JSONArray();
      for (changeSets = resultGetBaselines.getBaselinesInRepository().iterator(); ((Iterator)changeSets).hasNext();) { BaselineDTO baselineDTO = (BaselineDTO)((Iterator)changeSets).next();
        JSONPrintUtil.jsonizeResult(snapshot, baselines, Messages.CreateSnapshotCmd_BaselineSuccessfullyIncluded, 
          baselineDTO.getId(), baselineDTO.getName(), baselineDTO.getItemId(), repo.getRepositoryURI(), RepoUtil.ItemType.BASELINE);
      }
      
      if (baselines.size() > 0) {
        snapshot.put("baselines", baselines);
      }
      
      PendingChangesUtil.printSuccess(snapshot, config);
    }
  }
}
