package com.ibm.team.filesystem.cli.client.internal.portcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.changeset.ChangesetCommonOptions;
import com.ibm.team.filesystem.cli.client.internal.flowcommands.IConflictHandler;
import com.ibm.team.filesystem.cli.client.internal.flowcommands.conflicthandlers.GapInPlaceConflictHandler;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsProcessAcceptQueue;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.CurrentPatchDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.ProcessAcceptQueueResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.VersionableChangeDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;












public class PortStartCmd
  extends AbstractPortSubcommand
{
  private static final NamedOptionDefinition OPT_CURRENT = new NamedOptionDefinition("c", "current", 0);
  private static final NamedOptionDefinition OPT_SINGLE = new NamedOptionDefinition("s", "single", 0);
  public static final NamedOptionDefinition OPT_INPLACE_CONFLICT_HANDLER = new NamedOptionDefinition("i", "in-place-markers", 0);
  
  public PortStartCmd() {}
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    validateCommonArguments(cli);
    
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
    
    ParmsProcessAcceptQueue parmsStart = new ParmsProcessAcceptQueue();
    workspace = targetWs;
    componentItemId = componentDto.getItemId();
    
    if ((cli.hasOption(OPT_CURRENT)) && (cli.hasOption(OPT_SINGLE))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_2_ARGUMENTS, 
        OPT_CURRENT.getName(), OPT_SINGLE.getName()));
    }
    
    if (cli.hasOption(OPT_CURRENT)) {
      action = "create_current_patch";
    } else if (cli.hasOption(OPT_SINGLE)) {
      action = "create_current_patch_with_auto";
    } else {
      action = "initiate_auto_patch";
    }
    
    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
    if (cli.hasOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED)) {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
    } else {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    ProcessAcceptQueueResultDTO result = null;
    try {
      result = client.postProcessAcceptQueue(parmsStart, null);
    }
    catch (TeamRepositoryException tre) {
      throw StatusHelper.wrap(Messages.PortStartCmd_FAILURE, tre, out);
    }
    
    if (result.isCancelled())
    {
      int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChanges());
      if (noOfUncheckedInChanges > 0) {
        throw StatusHelper.uncheckedInChanges(NLS.bind(Messages.AcceptCmd2_UNCHECKEDIN_ITEMS_PRESENT, 
          Integer.valueOf(noOfUncheckedInChanges), CommonOptions.OPT_OVERWRITE_UNCOMMITTED.getName()));
      }
    }
    
    CurrentPatchDTO currentPort = result.getCurrentPatch();
    
    if (cli.hasOption(OPT_CURRENT)) {
      out.println(Messages.PortStartCmd_MARKED_CURRENT_GUIDANCE);
    } else if (cli.hasOption(OPT_SINGLE)) {
      out.println(Messages.PortStartCmd_PROCESSED_CURRENT_GUIDANCE);
    }
    else if ((currentPort == null) && (result.getAcceptQueueSize() == 0L)) {
      out.println(Messages.PortCmd_NO_PENDING_PORTS_GUIDANCE);
    } else {
      out.println(Messages.PortStartCmd_SUCCESS);
    }
    


    if (cli.hasOption(OPT_INPLACE_CONFLICT_HANDLER)) {
      markInPlaceConflicts(config, targetWs, result, client, hasVerboseOption(cli));
      out.println(Messages.Conflicts_InPlaceMarkers_Help);
    }
    
    if (currentPort != null) {
      if (hasUnresolvedChanges(currentPort)) {
        ISubcommandDefinition defnTemp = SubcommandUtil.getClassSubCommandDefn(config, 
          PortResolveCmd.class);
        out.println(NLS.bind(Messages.PortCmd_RESOLVE_GUIDANCE, new String[] {
          config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp).toString() }));
      } else {
        ISubcommandDefinition defnTemp = SubcommandUtil.getClassSubCommandDefn(config, 
          CurrentPortCmd.class);
        out.println(NLS.bind(Messages.PortCmd_COMPLETE_GUIDANCE, new String[] {
          config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp).toString(), CurrentPortCmd.OPT_COMPLETE.getName() }));
      }
    }
    
    if (hasVerboseOption(cli)) {
      printPorts(repo, targetWs, componentDto.getItemId(), client, out, config);
    }
  }
  

  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    addVerboseToOptions(options);
    
    options.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP);
    options.addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS);
    options.addOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED, Messages.Common_FORCE_OVERWRITE_UNCOMMITTED);
    options.addOption(OPT_INPLACE_CONFLICT_HANDLER, Messages.AcceptCmdOptions_1);
    options.addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_CURRENT, Messages.PortCmdOption_CREATE_CURRENT_PORT, false)
      .addOption(OPT_SINGLE, Messages.PortCmdOption_PROCESS_SINGLE_PORT, false));
    options.addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.PortCmdOption_WORKSPACE);
    options.addOption(OPT_COMPONENT, Messages.PortCmdOption_COMPONENT);
    
    return options;
  }
  
  private void markInPlaceConflicts(IScmClientConfiguration config, ParmsWorkspace ws, ProcessAcceptQueueResultDTO updateResult, IFilesystemRestClient client, boolean verbose)
    throws FileSystemException
  {
    File cfaRootPath = SubcommandUtil.findAncestorCFARoot(config.getContext().getCurrentWorkingDirectory());
    
    if (cfaRootPath != null) {
      IPath cfaRoot = new Path(cfaRootPath.getAbsolutePath());
      
      CurrentPatchDTO currentPort = updateResult.getCurrentPatch();
      if (currentPort != null)
      {
        IConflictHandler handler = new GapInPlaceConflictHandler();
        handler.handleConflicts(cfaRoot, ws, currentPort, client, config);
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
}
