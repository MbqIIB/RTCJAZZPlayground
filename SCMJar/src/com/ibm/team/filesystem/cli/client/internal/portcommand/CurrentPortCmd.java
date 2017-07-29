package com.ibm.team.filesystem.cli.client.internal.portcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.changeset.ChangesetCommonOptions;
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
import com.ibm.team.filesystem.client.rest.parameters.ParmsAbortCurrentPatch;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCompleteCurrentPatch;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCompleteCurrentPortDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.AbortCurrentPatchResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.CompleteCurrentPatchResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.eclipse.osgi.util.NLS;












public class CurrentPortCmd
  extends AbstractPortSubcommand
{
  public static final NamedOptionDefinition OPT_COMPLETE = new NamedOptionDefinition(
    null, "complete", 0);
  public static final NamedOptionDefinition OPT_NO_LINK = new NamedOptionDefinition(
    null, "no-link", 0);
  
  public static final NamedOptionDefinition OPT_IGNORE_UNRESOLVED = new NamedOptionDefinition("i", "ignore-unresolved", 0);
  public static final NamedOptionDefinition OPT_ABORT = new NamedOptionDefinition(
    null, "abort", 0);
  
  public CurrentPortCmd() {}
  
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
    
    if ((!cli.hasOption(OPT_COMPLETE)) && (!cli.hasOption(OPT_ABORT))) {
      throw StatusHelper.argSyntax(Messages.CurrentPortCmd_MissingOption);
    }
    
    if (cli.hasOption(OPT_COMPLETE)) {
      portComplete(cli, client, targetWs, componentDto);
    }
    
    if (cli.hasOption(OPT_ABORT)) {
      portAbort(cli, client, targetWs, componentDto);
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    if (hasVerboseOption(cli)) {
      printPorts(repo, targetWs, componentDto.getItemId(), client, out, config);
    }
  }
  
  private void portComplete(ICommandLine cli, IFilesystemRestClient client, ParmsWorkspace targetWs, WorkspaceComponentDTO componentDto) throws FileSystemException
  {
    ParmsCompleteCurrentPatch parmsResolve = new ParmsCompleteCurrentPatch();
    workspace = targetWs;
    componentItemId = componentDto.getItemId();
    createLink = (!cli.hasOption(OPT_NO_LINK));
    
    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
    if (cli.hasOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED)) {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
    } else {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
    }
    
    completeCurrentPortDilemmaHandler = new ParmsCompleteCurrentPortDilemmaHandler();
    
    if (cli.hasOption(OPT_IGNORE_UNRESOLVED)) {
      completeCurrentPortDilemmaHandler.unresolvedChangesInstruction = "continue";
    } else {
      completeCurrentPortDilemmaHandler.unresolvedChangesInstruction = "cancel";
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    CompleteCurrentPatchResultDTO result = null;
    try {
      result = client.postCompleteCurrentPatch(parmsResolve, null);
    } catch (TeamRepositoryException tre) {
      throw StatusHelper.wrap(Messages.PortCompleteCmd_FAILURE, tre, out);
    }
    

    if (result.isCancelled())
    {
      int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChanges());
      if (noOfUncheckedInChanges > 0) {
        throw StatusHelper.uncheckedInChanges(NLS.bind(Messages.AcceptCmd2_UNCHECKEDIN_ITEMS_PRESENT, 
          Integer.valueOf(noOfUncheckedInChanges), CommonOptions.OPT_OVERWRITE_UNCOMMITTED.getName()));
      }
      

      List unresolvedChanges = result.getUnresolvedChanges();
      if (unresolvedChanges != null) {
        int noOfUnresolvedChanges = unresolvedChanges.size();
        if (noOfUnresolvedChanges > 0) {
          throw StatusHelper.uncheckedInChanges(NLS.bind(Messages.PortCmd_UNRESOLVED_ITEMS_PRESENT, 
            Integer.valueOf(noOfUnresolvedChanges), OPT_IGNORE_UNRESOLVED.getName()));
        }
      }
    }
    
    if (result.getAcceptQueueSize() == 0L) {
      out.println(Messages.PortCmd_NO_PENDING_PORTS_GUIDANCE);
    } else {
      out.println(Messages.PortCompleteCmd_SUCCESS);
      ISubcommandDefinition defnTemp1 = SubcommandUtil.getClassSubCommandDefn(config, 
        PortStartCmd.class);
      ISubcommandDefinition defnTemp2 = SubcommandUtil.getClassSubCommandDefn(config, 
        PortReorderCmd.class);
      out.println(NLS.bind(Messages.PortCmd_RESUME_GUIDANCE, new String[] {
        config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp1).toString(), 
        SubcommandUtil.getExecutionString(defnTemp2).toString() }));
    }
  }
  
  private void portAbort(ICommandLine cli, IFilesystemRestClient client, ParmsWorkspace targetWs, WorkspaceComponentDTO componentDto)
    throws FileSystemException
  {
    ParmsAbortCurrentPatch parmsAbort = new ParmsAbortCurrentPatch();
    workspace = targetWs;
    componentItemIds = new String[] { componentDto.getItemId() };
    action = "make_pending";
    
    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
    if (cli.hasOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED)) {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
    } else {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    AbortCurrentPatchResultDTO result = null;
    try
    {
      result = client.postAbortCurrentPatch(parmsAbort, null);
    } catch (TeamRepositoryException tre) {
      throw StatusHelper.wrap(Messages.PortAbortCmd_FAILURE, tre, out);
    }
    
    if (result.isCancelled())
    {
      int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChanges());
      if (noOfUncheckedInChanges > 0) {
        throw StatusHelper.uncheckedInChanges(NLS.bind(Messages.AcceptCmd2_UNCHECKEDIN_ITEMS_PRESENT, 
          Integer.valueOf(noOfUncheckedInChanges), CommonOptions.OPT_OVERWRITE_UNCOMMITTED.getName()));
      }
    }
    out.println(Messages.PortAbortCmd_SUCCESS);
  }
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    addVerboseToOptions(options);
    
    options.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP);
    options.addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS);
    options.addOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED, Messages.Common_FORCE_OVERWRITE_UNCOMMITTED);
    options.addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.PortCmdOption_WORKSPACE);
    options.addOption(new ContinuousGroup()
      .addOption(OPT_COMPLETE, Messages.CurrentPortCmd_COMPLETE, true)
      .addOption(OPT_IGNORE_UNRESOLVED, Messages.PortCmdOption_FORCE_IGNORE_UNRESOLVED, false)
      .addOption(OPT_NO_LINK, Messages.PortCompleteCmd_NO_LINK, false))
      .addOption(new ContinuousGroup().addOption(OPT_ABORT, Messages.CurrentPortCmd_ABORT, true));
    options.addOption(OPT_COMPONENT, Messages.PortCmdOption_COMPONENT);
    
    return options;
  }
}
