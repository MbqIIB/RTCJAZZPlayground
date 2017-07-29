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
import com.ibm.team.filesystem.client.rest.parameters.ParmsCreateBaselineDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCreateBaselineRequest;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCreateBaselines;
import com.ibm.team.filesystem.client.rest.parameters.ParmsOutOfSyncInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.ProblemChangeSetsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.BaselineDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.workspace.CreateBaselineResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.PermissionDeniedException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.eclipse.osgi.util.NLS;








public class CreateBaselineCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public static final NamedOptionDefinition OPT_BASELINE_DESCRIPTION = new NamedOptionDefinition(
    null, "description", 1);
  
  public static final PositionalOptionDefinition OPT_WORKSPACE_NAME = new PositionalOptionDefinition(
    CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@");
  
  public static final PositionalOptionDefinition OPT_BASELINE_NAME = new PositionalOptionDefinition(
    "baseline-name", 1, 1);
  
  public static final NamedOptionDefinition OPT_COMPLETE = new NamedOptionDefinition(null, "complete", 0);
  
  public CreateBaselineCmd() {}
  
  public void run() throws FileSystemException { ICommandLine subargs = config.getSubcommandCommandLine();
    
    config.setEnableJSON(subargs.hasOption(CommonOptions.OPT_JSON));
    

    boolean allComponents = subargs.hasOption(CommonOptions.OPT_ALL);
    if ((allComponents) && (subargs.hasOption(CommonOptions.OPT_COMPONENTS_SELECTOR))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.CreateBaselineCmd_5, CommonOptions.OPT_ALL.getName()));
    }
    
    List<String> components = null;
    if (!allComponents) {
      if (!subargs.hasOption(CommonOptions.OPT_COMPONENTS_SELECTOR)) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.CreateBaselineCmd_6, CommonOptions.OPT_ALL.getName()));
      }
      List<IScmCommandLineArgument> compSelectors = ScmCommandLineArgument.createList(
        subargs.getOptionValues(CommonOptions.OPT_COMPONENTS_SELECTOR), config);
      SubcommandUtil.validateArgument(compSelectors, RepoUtil.ItemType.COMPONENT);
      components = RepoUtil.getSelectors(compSelectors);
    }
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(
      subargs.getOptionValue(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    

    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
    
    IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    ParmsWorkspace ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    

    WorkspaceDetailsDTO wsDetails = 
      (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
    

    List<String> baselineComponents = new ArrayList();
    
    Iterator localIterator = wsDetails.getComponents().iterator();
    Iterator<String> iterator;
    while (localIterator.hasNext()) {
      WorkspaceComponentDTO compDTO = (WorkspaceComponentDTO)localIterator.next();
      if (components != null) {
        iterator = components.iterator();
        while (iterator.hasNext()) {
          String compSelector = (String)iterator.next();
          IUuidAliasRegistry.IUuidAlias compAlias = RepoUtil.lookupUuidAndAlias(compSelector);
          if (((compAlias != null) && (compAlias.getUuid().getUuidValue().equals(compDTO.getItemId()))) || 
            (compSelector.equals(compDTO.getName()))) {
            baselineComponents.add(compDTO.getItemId());
            iterator.remove();
            break;
          }
        }
      } else {
        baselineComponents.add(compDTO.getItemId());
      }
    }
    

    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    if ((components != null) && (components.size() > 0)) {
      out.println(Messages.Common_COMP_NOT_FOUND_HEADER);
      for (String component : components) {
        out.indent().println(component);
      }
      throw StatusHelper.itemNotFound(Messages.Common_COMPS_NOT_FOUND);
    }
    

    BaselineDTO baselineDTO = createBaselines(ws, baselineComponents, wsFound.isStream(), client, config);
    
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(JSONPrintUtil.jsonizeBaseline(baselineDTO, new PendingChangesUtil.PendingChangesOptions()));
    } else {
      JSONObject baselineSuccess = JSONPrintUtil.jsonizeResult(Messages.CreateBaseLineCmd_Success_RETURN_UUID, baselineDTO.getName(), 
        baselineDTO.getItemId(), baselineDTO.getRepositoryURL(), RepoUtil.ItemType.BASELINE);
      PendingChangesUtil.printSuccess(baselineSuccess, config);
    }
  }
  











  private BaselineDTO createBaselines(ParmsWorkspace ws, List<String> baselineComponents, boolean isStream, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ICommandLine subargs = config.getSubcommandCommandLine();
    
    String baselineDescription = null;
    String baselineName = subargs.getOption(OPT_BASELINE_NAME);
    if (subargs.hasOption(OPT_BASELINE_DESCRIPTION)) {
      baselineDescription = subargs.getOption(OPT_BASELINE_DESCRIPTION);
    }
    
    ParmsCreateBaselines parms = new ParmsCreateBaselines();
    requests = new ParmsCreateBaselineRequest[baselineComponents.size()];
    int count = 0;
    for (String baselineComponent : baselineComponents) {
      ParmsCreateBaselineRequest parmsBaselineRequest = new ParmsCreateBaselineRequest();
      workspace = ws;
      componentItemId = baselineComponent;
      name = baselineName;
      comment = baselineDescription;
      requests[(count++)] = parmsBaselineRequest;
    }
    
    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    
    createBaselineDilemmaHandler = new ParmsCreateBaselineDilemmaHandler();
    createBaselineDilemmaHandler.conflictedConfigurationsDirection = "cancel";
    createBaselineDilemmaHandler.conflictedConfigurationsInSubcomponentsDirection = "cancel";
    createBaselineDilemmaHandler.inaccessibleConfigurationsInSubcomponentsDirection = "continue";
    createBaselineDilemmaHandler.notFoundConfigurationsInSubcomponentsDirection = "continue";
    
    if (subargs.hasOption(OPT_COMPLETE)) {
      createBaselineDilemmaHandler.activeChangeSetsInSubcomponentsDirection = "continue";
    } else {
      createBaselineDilemmaHandler.activeChangeSetsInSubcomponentsDirection = "cancel";
    }
    
    pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
    if (subargs.hasOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED)) {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
      pendingChangesDilemmaHandler.pendingChangesInSubcomponentsInstruction = "no";
    } else {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
      pendingChangesDilemmaHandler.pendingChangesInSubcomponentsInstruction = "cancel";
    }
    
    outOfSyncInstructions = new ParmsOutOfSyncInstructions();
    outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
    outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
    
    sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
    
    CreateBaselineResultDTO result = null;
    try {
      result = client.postCreateBaselines(parms, null);
    } catch (TeamRepositoryException e) {
      if ((e instanceof PermissionDeniedException)) {
        String message = e.getMessage();
        if ((e.getData() instanceof IComponentHandle)) {
          IComponentHandle inaccessibleComponent = (IComponentHandle)e.getData();
          message = isStream ? 
            Messages.CreateBaselineCmd_ERROR_COMPONENT_PERMISSION_STREAM : 
            Messages.CreateBaselineCmd_ERROR_COMPONENT_PERMISSION_REPOSITORY_WORKSPACE;
          message = NLS.bind(message, inaccessibleComponent.getItemId().getUuidValue());
        }
        throw StatusHelper.permissionFailure(message);
      }
      throw StatusHelper.wrap(Messages.CreateBaselineCmd_14, e, new IndentingPrintStream(
        config.getContext().stderr()), repositoryUrl);
    }
    

    if (result.isCancelled()) {
      if (result.getOutOfSyncShares().size() > 0) {
        AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
      }
      
      int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result
        .getConfigurationsWithUncheckedInChanges());
      
      noOfUncheckedInChanges = noOfUncheckedInChanges + SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChangesInSubcomponents());
      if (noOfUncheckedInChanges > 0) {
        throw StatusHelper.uncheckedInChanges(NLS.bind(
          Messages.AcceptCmd2_UNCHECKEDIN_ITEMS_PRESENT, Integer.valueOf(noOfUncheckedInChanges), 
          CommonOptions.OPT_OVERWRITE_UNCOMMITTED.getName()));
      }
      
      int noOfConflicts = result.getConfigurationsWithConflicts().size();
      if (noOfConflicts > 0) {
        ISubcommandDefinition defnTemp1 = SubcommandUtil.getClassSubCommandDefn(config, ResolveCmd.class);
        ISubcommandDefinition defnTemp2 = SubcommandUtil.getClassSubCommandDefn(config, ConflictsCmd.class);
        ISubcommandDefinition defnTemp3 = SubcommandUtil.getClassSubCommandDefn(config, StatusCmd.class);
        
        throw 
          StatusHelper.conflict(Messages.CreateBaselineCmd_ERROR_UNRESOLVED_REMOTE_CONFLICTS + 
          " " + NLS.bind(Messages.AcceptCmd_CONFLICT_GUIDANCE, 
          new String[] {
          config.getContext().getAppName(), 
          SubcommandUtil.getExecutionString(defnTemp1).toString(), 
          SubcommandUtil.getExecutionString(defnTemp2).toString(), 
          SubcommandUtil.getExecutionString(defnTemp3).toString() }));
      }
      
      noOfConflicts = result.getConfigurationsWithConflictsInSubcomponents().size();
      if (noOfConflicts > 0) {
        ISubcommandDefinition defnTemp1 = SubcommandUtil.getClassSubCommandDefn(config, ResolveCmd.class);
        ISubcommandDefinition defnTemp2 = SubcommandUtil.getClassSubCommandDefn(config, ConflictsCmd.class);
        ISubcommandDefinition defnTemp3 = SubcommandUtil.getClassSubCommandDefn(config, StatusCmd.class);
        
        throw 
          StatusHelper.conflict(Messages.CreateBaselineCmd_ERROR_UNRESOLVED_REMOTE_CONFLICTS_SUBCOMPONENTS + 
          " " + NLS.bind(Messages.AcceptCmd_CONFLICT_GUIDANCE, 
          new String[] {
          config.getContext().getAppName(), 
          SubcommandUtil.getExecutionString(defnTemp1).toString(), 
          SubcommandUtil.getExecutionString(defnTemp2).toString(), 
          SubcommandUtil.getExecutionString(defnTemp3).toString() }));
      }
      

      List<ProblemChangeSetsDTO> activeChangeSets = result.getActiveChangeSetsInSubcomponents();
      if ((activeChangeSets.size() != 0) && (!config.isJSONEnabled())) {
        IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
        err.println(Messages.CreateBaselineCmd_activeChangeSetsHeader);
        Iterator localIterator3; for (Iterator localIterator2 = activeChangeSets.iterator(); localIterator2.hasNext(); 
            









            localIterator3.hasNext())
        {
          ProblemChangeSetsDTO changeSet = (ProblemChangeSetsDTO)localIterator2.next();
          List<ChangeSetSyncDTO> changeSets = PendingChangesUtil.getChangeSets2(
            changeSet.getRepositoryURL(), 
            changeSet.getChangeSetItemIds(), 
            false, 
            client, 
            config);
          IndentingPrintStream indent = err.indent();
          PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
          options.enablePrinter(4);
          
          localIterator3 = changeSets.iterator(); continue;ChangeSetSyncDTO cs = (ChangeSetSyncDTO)localIterator3.next();
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
        

        throw StatusHelper.activeCSFailure(NLS.bind(Messages.CreateBaselineCmd_ERROR_ACTIVE_CHANGESETS, OPT_COMPLETE.getName()));
      }
    }
    

    BaselineDTO baselineDTO = (BaselineDTO)result.getBaselines().get(0);
    
    return baselineDTO;
  }
  
  public Options getOptions() throws ConflictingOptionException
  {
    Options options = new Options(false, true);
    
    options.setLongHelp(Messages.CreateBaselineCmd_15);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(OPT_BASELINE_DESCRIPTION, Messages.CreateBaselineCmd_16)
      .addOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED, Messages.CreateBaselineCmd_HELP_IGNORE_UNCOMMITTED_LOCAL_CHANGES)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(OPT_COMPLETE, Messages.CreateBaselineCmd_COMPLETE_HELP, false)
      .addOption(OPT_WORKSPACE_NAME, Messages.CreateBaselineCmd_17)
      .addOption(OPT_BASELINE_NAME, Messages.CreateBaselineCmd_18)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(CommonOptions.OPT_ALL, CommonOptions.OPT_ALL_HELP, false)
      .addOption(CommonOptions.OPT_COMPONENTS_SELECTOR, Messages.Common_SELECT_COMPONENTS_HELP));
    
    return options;
  }
}
