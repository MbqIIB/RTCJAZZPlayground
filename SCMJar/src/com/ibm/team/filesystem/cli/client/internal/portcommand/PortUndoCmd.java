package com.ibm.team.filesystem.cli.client.internal.portcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.changeset.ChangesetCommonOptions;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsUpdateCurrentPatch;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.UpdateCurrentPatchResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.util.NLS;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;








public class PortUndoCmd
  extends AbstractPortSubcommand
{
  protected static final PositionalOptionDefinition OPT_COMPONENT = new PositionalOptionDefinition("component", 1, 1);
  public static final NamedOptionDefinition OPT_CHANGES = new NamedOptionDefinition("c", "changes", -1, "@");
  
  public PortUndoCmd() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    addVerboseToOptions(options);
    
    options.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP);
    options.addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS);
    options.addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.PortCmdOption_WORKSPACE);
    options.addOption(OPT_CHANGES, Messages.PortUndoCmdOption_CHANGES_TO_UNDO);
    options.addOption(CommonOptions.OPT_POSITIONAL_ARG_SEPARATOR, NLS.bind(Messages.PortResolveCmdOption_SEPARATOR, OPT_CHANGES.getName(), new Object[0]));
    options.addOption(OPT_COMPONENT, Messages.PortCmdOption_COMPONENT);
    
    return options;
  }
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    validateCommonArguments(cli);
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    IScmCommandLineArgument workspaceSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_WORKSPACE, null), config);
    
    ParmsWorkspace targetWs = new ParmsWorkspace();
    ITeamRepository repo = null;
    
    if (workspaceSelector == null) {
      List<ISandboxWorkspace> wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, config);
      if (wsInSandboxList.size() != 1) {
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.PortCmdOption_AmbiguousWorkspace, 
          cli.getDefinition().getOption(OPT_WORKSPACE).getName(), new Object[0]));
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
    
    IScmCommandLineArgument componentSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT), config);
    WorkspaceComponentDTO componentDto = RepoUtil.getComponent(targetWs, componentSelector.getItemSelector(), client, config);
    
    Set<String> changesToUndo = new HashSet();
    if (cli.hasOption(OPT_CHANGES)) {
      List<IScmCommandLineArgument> argChanges = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_CHANGES), config);
      SubcommandUtil.validateArgument(argChanges, new RepoUtil.ItemType[] { RepoUtil.ItemType.VERSIONABLE, RepoUtil.ItemType.CHANGE });
      List<String> selChanges = RepoUtil.getSelectors(argChanges);
      
      for (int i = 0; i < selChanges.size(); i++) {
        UUID uuid = RepoUtil.lookupUuid((String)selChanges.get(i));
        if (uuid == null) {
          throw StatusHelper.itemNotFound(NLS.bind(Messages.UndoCmd_0, selChanges.get(i), new Object[0]));
        }
        changesToUndo.add(uuid.getUuidValue());
      }
    }
    
    if (changesToUndo.isEmpty()) {
      throw StatusHelper.argSyntax(Messages.PortUndoCmd_NO_CHANGES);
    }
    
    String action = "undo";
    
    ParmsUpdateCurrentPatch parms = new ParmsUpdateCurrentPatch();
    workspace = targetWs;
    componentItemId = componentDto.getItemId();
    changeIds = ((String[])changesToUndo.toArray(new String[changesToUndo.size()]));
    action = action;
    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    try
    {
      UpdateCurrentPatchResultDTO resultDto = client.postUpdateCurrentPatch(parms, null);
      printResult(resultDto, out);
    } catch (TeamRepositoryException tre) {
      throw StatusHelper.wrap(Messages.PortUndoCmd_FAILURE, tre, out);
    }
    
    if (hasVerboseOption(cli)) {
      printPorts(repo, targetWs, componentDto.getItemId(), client, out, config);
    }
  }
  
  protected void validateCommonArguments(ICommandLine cli) throws FileSystemException
  {
    IScmCommandLineArgument workspace = ScmCommandLineArgument.create(cli.getOptionValue(OPT_WORKSPACE, null), config);
    SubcommandUtil.validateArgument(workspace, RepoUtil.ItemType.WORKSPACE);
    
    IScmCommandLineArgument component = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT.getId()), config);
    SubcommandUtil.validateArgument(component, RepoUtil.ItemType.COMPONENT);
  }
  
  private void printResult(UpdateCurrentPatchResultDTO result, IndentingPrintStream out) {
    if (result.isSetUpdateDilemma()) {
      out.println(Messages.PortUndoCmd_FAILURE);
    } else {
      out.println(Messages.PortUndoCmd_SUCCESS);
    }
  }
}
