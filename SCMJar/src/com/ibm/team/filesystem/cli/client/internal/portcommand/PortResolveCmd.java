package com.ibm.team.filesystem.cli.client.internal.portcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.changeset.ChangesetCommonOptions;
import com.ibm.team.filesystem.cli.client.internal.flowcommands.IConflictHandler;
import com.ibm.team.filesystem.cli.client.internal.flowcommands.conflicthandlers.GapInPlaceConflictHandler;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsUpdateCurrentPatch;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.ChangeDetailDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.ContentChangeDetailDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.CurrentPatchDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.UpdateCurrentPatchResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.VersionableChangeDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLineArgument;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.subcommands.HelpCmd;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.ProducesOrphansInConfigurationException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;













public class PortResolveCmd
  extends AbstractPortSubcommand
{
  protected static final PositionalOptionDefinition OPT_COMPONENT = new PositionalOptionDefinition("component", 1, 1);
  public static final NamedOptionDefinition OPT_CHANGES = new NamedOptionDefinition("c", "changes", -1, "@");
  public static final NamedOptionDefinition OPT_RESOLVE_WITH_MINE = new NamedOptionDefinition("m", "mine", 0);
  public static final NamedOptionDefinition OPT_RESOLVE_WITH_PROPOSED = new NamedOptionDefinition("p", "proposed", 0);
  public static final NamedOptionDefinition OPT_INPLACE_CONFLICT_HANDLER = new NamedOptionDefinition("i", "in-place-markers", 0);
  public static final NamedOptionDefinition OPT_PARENT = new NamedOptionDefinition(null, "parent", 1);
  
  public PortResolveCmd() {}
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    validateCommonArguments(cli);
    
    boolean automergeDisabled = (cli.hasOption(OPT_RESOLVE_WITH_MINE)) || (cli.hasOption(OPT_RESOLVE_WITH_PROPOSED));
    if ((automergeDisabled) && (cli.hasOption(OPT_INPLACE_CONFLICT_HANDLER))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.PortResolveCmd_INVALID_INPLACE_MARKER_REQUEST, 
        new String[] { OPT_INPLACE_CONFLICT_HANDLER.getName(), 
        OPT_RESOLVE_WITH_MINE.getName(), OPT_RESOLVE_WITH_PROPOSED.getName() }));
    }
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    IScmCommandLineArgument workspaceSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_WORKSPACE, null), config);
    IScmCommandLineArgument componentSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT), config);
    
    ParmsWorkspace targetWs = new ParmsWorkspace();
    ITeamRepository repo = null;
    
    if (workspaceSelector == null) {
      List<ISandboxWorkspace> wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, config);
      if (wsInSandboxList.size() != 1) {
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.PortCmdOption_AmbiguousWorkspace, 
          cli.getDefinition().getOption(OPT_WORKSPACE).getName()));
      }
      
      ISandboxWorkspace wsInSandbox = (ISandboxWorkspace)wsInSandboxList.iterator().next();
      repositoryUrl = RepoUtil.getRepoUri(config, client, wsInSandbox.getRepositoryId(), 
        Collections.singletonList(wsInSandbox));
      workspaceItemId = wsInSandbox.getWorkspaceItemId();
      
      repo = RepoUtil.login(config, client, config.getConnectionInfo(repositoryUrl));
    } else {
      repo = RepoUtil.loginUrlArgAncestor(config, client, workspaceSelector);
      
      IWorkspace ws = RepoUtil.getWorkspace(workspaceSelector.getItemSelector(), true, false, repo, config);
      
      repositoryUrl = repo.getRepositoryURI();
      workspaceItemId = ws.getItemId().getUuidValue();
    }
    
    WorkspaceComponentDTO componentDto = RepoUtil.getComponent(targetWs, componentSelector.getItemSelector(), client, config);
    


    Set<String> changesToResolve = new HashSet();
    if (cli.hasOption(OPT_CHANGES)) {
      List<IScmCommandLineArgument> argChanges = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_CHANGES), config);
      List<String> selChanges = RepoUtil.getSelectors(argChanges);
      
      for (int i = 0; i < selChanges.size(); i++) {
        UUID uuid = RepoUtil.lookupUuid((String)selChanges.get(i));
        if (uuid == null) {
          throw StatusHelper.itemNotFound(NLS.bind(Messages.UndoCmd_0, selChanges.get(i)));
        }
        changesToResolve.add(uuid.getUuidValue());
      }
    }
    
    if (changesToResolve.isEmpty())
    {
      throw StatusHelper.argSyntax(Messages.PortResolveCmd_InvalidOptionMissingChanges);
    }
    
    ParmsUpdateCurrentPatch parms = new ParmsUpdateCurrentPatch();
    

    String action = "autoresolve";
    if (cli.hasOption(OPT_RESOLVE_WITH_MINE)) {
      action = "mark_as_resolved";
    } else if (cli.hasOption(OPT_RESOLVE_WITH_PROPOSED)) {
      action = "resolve_with_proposed";
    } else if (cli.hasOption(OPT_PARENT))
    {
      action = "reparent";
      
      IScmCommandLineArgument argParent = ScmCommandLineArgument.create(cli.getOptionValue(OPT_PARENT), config);
      SubcommandUtil.validateArgument(argParent, RepoUtil.ItemType.VERSIONABLE);
      String selParent = argParent.getItemSelector();
      ILocation locParent = SubcommandUtil.makeAbsolutePath(config, selParent);
      try
      {
        ResourcePropertiesDTO propsParent = RepoUtil.getResourceProperties(locParent.toOSString(), 
          SubcommandUtil.shouldRefreshFileSystem(config), client, config, false);
        newParenId = propsParent.getItemId();
      } catch (Exception localException) {
        UUID uuid = RepoUtil.lookupUuid(selParent);
        if (uuid == null) {
          throw StatusHelper.itemNotFound(NLS.bind(Messages.UndoCmd_0, selParent));
        }
        newParenId = uuid.getUuidValue();
      }
    }
    
    workspace = targetWs;
    componentItemId = componentDto.getItemId();
    changeIds = ((String[])changesToResolve.toArray(new String[changesToResolve.size()]));
    bestEffort = Boolean.valueOf(true);
    force = Boolean.valueOf(true);
    
    action = action;
    
    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
    if (cli.hasOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED)) {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
    } else {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    UpdateCurrentPatchResultDTO resultDto = null;
    ISubcommandDefinition defnTemp1;
    try { resultDto = client.postUpdateCurrentPatch(parms, null);
    }
    catch (TeamRepositoryException tre) {
      ProducesOrphansInConfigurationException poe = (ProducesOrphansInConfigurationException)SubcommandUtil.findExceptionByType(ProducesOrphansInConfigurationException.class, tre);
      if (poe != null) {
        defnTemp1 = SubcommandUtil.getClassSubCommandDefn(config, 
          HelpCmd.class);
        ISubcommandDefinition defnTemp2 = SubcommandUtil.getClassSubCommandDefn(config, 
          PortResolveCmd.class);
        throw StatusHelper.orphan(NLS.bind(Messages.PortResolveCmd_PRODUCES_ORPHANS, new String[] {
          OPT_PARENT.getName(), config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp1).toString(), 
          SubcommandUtil.getExecutionString(defnTemp2).toString() }), 
          null);
      }
      
      throw StatusHelper.wrap(Messages.PortResolveCmd_FAILURE, tre, out);
    }
    
    String autoResolution = null;
    
    if (action == "autoresolve") {
      if (cli.hasOption(OPT_INPLACE_CONFLICT_HANDLER)) {
        List<VersionableChangeDTO> unresolveChanges = getUnresolvedChanges(changesToResolve, resultDto);
        markInPlaceConflicts(config, targetWs, resultDto, unresolveChanges, client, hasVerboseOption(cli));
        autoResolution = Messages.Conflicts_InPlaceMarkers_Help;
      } else {
        for (String change : changesToResolve) {
          if ((!resultDto.getResolvedVersionableIds().contains(change)) && 
            (!resultDto.getResolvedChangeDetailIds().contains(change))) {
            autoResolution = Messages.PortResolveCmd_CANNOT_AUTORESOLVE;
            break;
          }
        }
      }
    }
    
    printResult(resultDto, autoResolution, out);
    
    if (!hasUnresolvedChanges(resultDto.getCurrentPatch())) {
      ISubcommandDefinition defnTemp = SubcommandUtil.getClassSubCommandDefn(config, 
        PortResolveCmd.class);
      out.println(NLS.bind(Messages.PortCmd_COMPLETE_GUIDANCE, new String[] {
        config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp).toString(), CurrentPortCmd.OPT_COMPLETE.getName() }));
    }
    
    if (hasVerboseOption(cli)) {
      printPorts(repo, targetWs, componentDto.getItemId(), client, out, config);
    }
  }
  
  public Options getOptions() throws ConflictingOptionException
  {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    addVerboseToOptions(options);
    
    options.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP);
    options.addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS);
    options.addOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED, Messages.Common_FORCE_OVERWRITE_UNCOMMITTED);
    options.addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.PortCmdOption_WORKSPACE);
    options.addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_RESOLVE_WITH_MINE, Messages.PortResolveCmdOption_RESOLVE_WITH_MINE, false)
      .addOption(OPT_RESOLVE_WITH_PROPOSED, Messages.PortResolveCmdOption_RESOLVE_WITH_PROPOSED, false)
      .addOption(OPT_INPLACE_CONFLICT_HANDLER, Messages.AcceptCmdOptions_1, false)
      .addOption(OPT_PARENT, Messages.PortResolveCmdOption_PARENT, false));
    options.addOption(OPT_CHANGES, Messages.PortResolveCmdOption_CHANGES_TO_RESOLVE, true);
    options.addOption(CommonOptions.OPT_POSITIONAL_ARG_SEPARATOR, NLS.bind(Messages.PortResolveCmdOption_SEPARATOR, OPT_CHANGES.getName()));
    options.addOption(OPT_COMPONENT, Messages.PortCmdOption_COMPONENT);
    
    return options;
  }
  
  protected void validateCommonArguments(ICommandLine cli) throws FileSystemException
  {
    IScmCommandLineArgument workspace = ScmCommandLineArgument.create(cli.getOptionValue(OPT_WORKSPACE, null), config);
    SubcommandUtil.validateArgument(workspace, RepoUtil.ItemType.WORKSPACE);
    
    IScmCommandLineArgument component = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT.getId()), config);
    SubcommandUtil.validateArgument(component, RepoUtil.ItemType.COMPONENT);
    
    boolean hasChanges = cli.hasOption(OPT_CHANGES);
    if (!hasChanges) {
      throw StatusHelper.argSyntax(Messages.PortResolveCmd_InvalidOptionMissingChanges);
    }
    List<ICommandLineArgument> argChanges = cli.getOptionValues(OPT_CHANGES);
    int countChanges = hasChanges ? argChanges.size() : 0;
    if ((hasChanges) && (countChanges <= 0)) {
      throw StatusHelper.argSyntax(Messages.PortCmd_UnexpectedArguments);
    }
    List<IScmCommandLineArgument> changes = ScmCommandLineArgument.createList(argChanges, config);
    SubcommandUtil.validateArgument(changes, new RepoUtil.ItemType[] { RepoUtil.ItemType.VERSIONABLE, RepoUtil.ItemType.CHANGE });
  }
  
  private void printResult(UpdateCurrentPatchResultDTO result, String autoResolution, IndentingPrintStream out) throws CLIFileSystemClientException {
    if (result.isCancelled())
    {
      int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChanges());
      if (noOfUncheckedInChanges > 0) {
        throw StatusHelper.uncheckedInChanges(NLS.bind(Messages.AcceptCmd2_UNCHECKEDIN_ITEMS_PRESENT, 
          Integer.valueOf(noOfUncheckedInChanges), CommonOptions.OPT_OVERWRITE_UNCOMMITTED.getName()));
      }
    }
    
    if (result.isSetUpdateDilemma()) {
      out.println(Messages.PortResolveCmd_FAILURE);
    }
    else if (autoResolution == null) {
      out.println(Messages.PortResolveCmd_SUCCESS);
    } else {
      out.println(autoResolution);
    }
  }
  

  private void markInPlaceConflicts(IScmClientConfiguration config, ParmsWorkspace ws, UpdateCurrentPatchResultDTO updateResult, List<VersionableChangeDTO> unresolvedChanges, IFilesystemRestClient client, boolean verbose)
    throws FileSystemException
  {
    File cfaRootPath = SubcommandUtil.findAncestorCFARoot(config.getContext().getCurrentWorkingDirectory());
    
    if (cfaRootPath != null) {
      IPath cfaRoot = new Path(cfaRootPath.getAbsolutePath());
      
      CurrentPatchDTO currentPort = updateResult.getCurrentPatch();
      if (currentPort != null)
      {
        IConflictHandler handler = new GapInPlaceConflictHandler();
        handler.handleConflicts(cfaRoot, ws, currentPort, unresolvedChanges, client, config);
      }
    }
  }
  
  private boolean hasUnresolvedChanges(CurrentPatchDTO currentPort)
  {
    boolean hasUnresolved = false;
    for (VersionableChangeDTO changeDTO : currentPort.getChanges()) {
      if (!changeDTO.isResolved()) {
        hasUnresolved = true;
        break;
      }
    }
    return hasUnresolved;
  }
  

  private List<VersionableChangeDTO> getUnresolvedChanges(Set<String> changesToResolve, UpdateCurrentPatchResultDTO resultDto)
  {
    Set<String> unresolvedChangeIds = new HashSet();
    
    for (String idString : changesToResolve) {
      if ((!resultDto.getResolvedVersionableIds().contains(idString)) && (!resultDto.getResolvedChangeDetailIds().contains(idString))) {
        unresolvedChangeIds.add(idString);
      }
    }
    
    List<VersionableChangeDTO> unresolvedChanges = new ArrayList();
    CurrentPatchDTO currentPort = resultDto.getCurrentPatch();
    for (VersionableChangeDTO changeDTO : currentPort.getChanges()) {
      if (unresolvedChangeIds.contains(changeDTO.getVersionableItemId())) {
        unresolvedChanges.add(changeDTO);
      } else {
        for (ChangeDetailDTO detailDTO : changeDTO.getChangeDetails()) {
          if ((unresolvedChangeIds.contains(detailDTO.getId())) && ((detailDTO instanceof ContentChangeDetailDTO))) {
            unresolvedChanges.add(changeDTO);
            break;
          }
        }
      }
    }
    return unresolvedChanges;
  }
}
