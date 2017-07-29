package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptResultDisplayer;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.IRelativeLocation;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.internal.RelativeLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsComponentChange;
import com.ibm.team.filesystem.client.rest.parameters.ParmsConfigurationChanges;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeletedContentDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsOutOfSyncInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPutWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceUpdateDilemmaHandler;
import com.ibm.team.filesystem.common.internal.rest.client.core.ComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.workspace.PutWorkspaceResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.osgi.util.NLS;






public class RemoveComponentsCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public RemoveComponentsCmd() {}
  
  public static final IOptionKey OPT_COMPONENTS = new OptionKey("components");
  
  public static final NamedOptionDefinition OPT_OVERWRITE_UNCOMMITTED_DEPRECATED = new NamedOptionDefinition("o", "overwrite-uncommitted", 0);
  
  public static final NamedOptionDefinition OPT_IGNORE_UNCOMMITTED = new NamedOptionDefinition("i", "ignore-uncommitted", 0);
  
  public static final NamedOptionDefinition OPT_NO_BACKUP = new NamedOptionDefinition("n", "nobackup", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    OPT_OVERWRITE_UNCOMMITTED_DEPRECATED.hideOption();
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(OPT_OVERWRITE_UNCOMMITTED_DEPRECATED, Messages.Common_FORCE_OVERWRITE_UNCOMMITTED)
      .addOption(OPT_IGNORE_UNCOMMITTED, Messages.RemoveComponentsCmd_IGNORE_UNCOMMITTED)
      .addOption(OPT_NO_BACKUP, Messages.RemoveComponentsCmd_NOBACKUP)
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@"), Messages.RemoveComponentsCmd_WorkspaceToRemoveComponents)
      .addOption(new PositionalOptionDefinition(OPT_COMPONENTS, "components", 1, -1), Messages.RemoveComponentsCmd_ComponentsToRemove);
    return options;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    

    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
    

    IWorkspace ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    

    ParmsWorkspace targetWs = new ParmsWorkspace(repo.getRepositoryURI(), ws.getItemId().getUuidValue());
    WorkspaceDetailsDTO targetWsDetails = 
      (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(targetWs), client, config).get(0);
    List<WorkspaceComponentDTO> targetWsComps = targetWsDetails.getComponents();
    

    Map<String, WorkspaceComponentDTO> targetWsCompIds = new HashMap(targetWsComps.size());
    for (WorkspaceComponentDTO comp : targetWsComps) {
      targetWsCompIds.put(comp.getItemId(), comp);
    }
    

    List<IScmCommandLineArgument> componentSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_COMPONENTS), config);
    SubcommandUtil.validateArgument(componentSelectors, RepoUtil.ItemType.COMPONENT);
    RepoUtil.validateItemRepos(RepoUtil.ItemType.COMPONENT, componentSelectors, repo, config);
    Object compSelectors = RepoUtil.getSelectors(componentSelectors);
    

    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    boolean printSkipHeader = true;
    Map<String, WorkspaceComponentDTO> filteredComponents = new HashMap();
    for (String compSelector : (List)compSelectors) {
      boolean found = false;
      IUuidAliasRegistry.IUuidAlias compAlias = RepoUtil.lookupUuidAndAlias(compSelector, repo.getRepositoryURI());
      for (WorkspaceComponentDTO compDTO : targetWsComps) {
        if (((compAlias != null) && (compAlias.getUuid().getUuidValue().equals(compDTO.getItemId()))) || 
          (compSelector.equals(compDTO.getName()))) {
          filteredComponents.put(compDTO.getItemId(), compDTO);
          found = true;
          break;
        }
      }
      
      if (!found) {
        if (printSkipHeader) {
          out.println(Messages.RemoveComponentsCmd_ComponentNotFoundInWorkspace);
          printSkipHeader = false;
        }
        out.indent().println(compSelector);
      }
    }
    
    if (!filteredComponents.isEmpty()) {
      putWorkspace(config, client, repo, ws, filteredComponents);
    }
  }
  



  private void putWorkspace(IScmClientConfiguration config, IFilesystemRestClient client, ITeamRepository repo, IWorkspace workspace, Map<String, WorkspaceComponentDTO> compIdToObj)
    throws FileSystemException
  {
    validateComponentRemoval(config, client, repo, workspace, compIdToObj);
    
    ParmsPutWorkspace parms = new ParmsPutWorkspace();
    configurationChanges = new ParmsConfigurationChanges();
    configurationChanges.sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    configurationChanges.sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
    configurationChanges.sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
    
    configurationChanges.outOfSyncInstructions = new ParmsOutOfSyncInstructions();
    configurationChanges.outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
    configurationChanges.outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
    
    configurationChanges.pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
    if ((config.getSubcommandCommandLine().hasOption(OPT_OVERWRITE_UNCOMMITTED_DEPRECATED)) || (config.getSubcommandCommandLine().hasOption(OPT_IGNORE_UNCOMMITTED))) {
      configurationChanges.pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
    } else {
      configurationChanges.pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
    }
    
    workspace = new ParmsWorkspace();
    workspace.repositoryUrl = repo.getRepositoryURI();
    workspace.workspaceItemId = workspace.getItemId().getUuidValue();
    configurationChanges.preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    configurationChanges.components = new ParmsComponentChange[compIdToObj.size()];
    configurationChanges.hierarchicalOperation = true;
    
    List<ParmsComponentChange> compChanges = new ArrayList();
    for (Map.Entry<String, WorkspaceComponentDTO> entry : compIdToObj.entrySet()) {
      ParmsComponentChange change = new ParmsComponentChange();
      cmd = "removeComponent";
      componentItemId = ((String)entry.getKey());
      compChanges.add(change);
    }
    configurationChanges.components = ((ParmsComponentChange[])compChanges.toArray(new ParmsComponentChange[compChanges.size()]));
    ParmsWorkspaceUpdateDilemmaHandler parmsWorkspaceUpdateDilemmaHandler = new ParmsWorkspaceUpdateDilemmaHandler();
    if (config.getSubcommandCommandLine().hasOption(OPT_NO_BACKUP)) {
      noBackupBaselineDirection = "continue";
    } else {
      noBackupBaselineDirection = "cancel";
    }
    
    componentFlowDirection = "continue";
    
    disconnectedComponentsDirection = "continue";
    configurationChanges.workspaceUpdateDilemmaHandler = parmsWorkspaceUpdateDilemmaHandler;
    

    try
    {
      result = client.postPutWorkspace(parms, null);
    } catch (TeamRepositoryException e) { PutWorkspaceResultDTO result;
      throw StatusHelper.wrap(Messages.RemoveComponentsCmd_CannotRemoveComponents, e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    PutWorkspaceResultDTO result;
    if (result.isCancelled()) {
      if (result.getOutOfSyncShares().size() > 0) {
        AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
      }
      
      int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChanges());
      if (noOfUncheckedInChanges > 0) {
        throw StatusHelper.uncheckedInChanges(NLS.bind(Messages.AcceptCmd2_UNCHECKEDIN_ITEMS_PRESENT, Integer.valueOf(noOfUncheckedInChanges), OPT_IGNORE_UNCOMMITTED.getName()));
      }
      if (result.getNoBackupBaselinesComponents().size() > 0) {
        throw StatusHelper.noBackupBaseline(NLS.bind(Messages.RemoveComponentsCmd_NO_BACKUP_BASELINE, OPT_NO_BACKUP.getName()));
      }
    }
    
    printResult(config, result, repo);
  }
  
  private void validateComponentRemoval(IScmClientConfiguration config, IFilesystemRestClient client, ITeamRepository repo, IWorkspace workspace, Map<String, WorkspaceComponentDTO> compIdToObj)
    throws FileSystemException
  {
    ILocation cwd = new PathLocation(config.getContext().getCurrentWorkingDirectory());
    File cfaRoot = SubcommandUtil.findAncestorCFARoot((File)cwd.getAdapter(File.class));
    if (cfaRoot != null)
    {
      List<ShareDTO> shares = RepoUtil.getSharesInSandbox(null, cwd, client, config);
      for (ShareDTO share : shares) {
        IRelativeLocation sharePath = new RelativeLocation(share.getPath().getSegments());
        ILocation shareAbsPath = new PathLocation(share.getSandboxPath()).append(sharePath);
        
        if ((shareAbsPath.isPrefixOf(cwd)) && (compIdToObj.containsKey(share.getComponentItemId()))) {
          throw StatusHelper.inappropriateArgument(NLS.bind(Messages.RemoveComponentsCmd_CannotUnloadCWD, 
            AliasUtil.selector(share.getComponentName(), UUID.valueOf(share.getComponentItemId()), 
            RepoUtil.getRepoUri(config, client, share), RepoUtil.ItemType.COMPONENT)));
        }
      }
    }
  }
  

  private void printResult(IScmClientConfiguration config, PutWorkspaceResultDTO result, ITeamRepository repo)
    throws FileSystemException
  {
    for (ComponentDTO component : result.getComponentsRemoved()) {
      config.getContext().stdout().println(NLS.bind(Messages.RemoveComponentsCmd_ComponentSuccessfullyRemoved, 
        AliasUtil.selector(component.getName(), UUID.valueOf(component.getItemId()), repo.getRepositoryURI(), 
        RepoUtil.ItemType.COMPONENT)));
    }
  }
}
