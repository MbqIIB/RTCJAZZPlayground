package com.ibm.team.filesystem.cli.client.internal.portcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.changeset.ChangesetCommonOptions;
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
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsAddToAcceptQueue;
import com.ibm.team.filesystem.client.rest.parameters.ParmsReorderAcceptQueue;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.eclipse.osgi.util.NLS;









public class PortReorderCmd
  extends AbstractPortSubcommand
{
  protected static final PositionalOptionDefinition OPT_COMPONENT = new PositionalOptionDefinition("component", 1, 1);
  public static final NamedOptionDefinition OPT_ORDER = new NamedOptionDefinition("o", "order", -1);
  public static final NamedOptionDefinition OPT_AFTER = new NamedOptionDefinition("a", "after", 1);
  public static final NamedOptionDefinition OPT_ADD = new NamedOptionDefinition(null, "add", -1);
  
  public PortReorderCmd() {}
  
  public void run() throws FileSystemException { ICommandLine cli = config.getSubcommandCommandLine();
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
    
    if ((!cli.hasOption(OPT_ORDER)) && (!cli.hasOption(OPT_ADD)))
      throw StatusHelper.argSyntax(Messages.PortReorderCmd_MissingOption);
    if ((cli.hasOption(OPT_ORDER)) && (cli.hasOption(OPT_ADD))) {
      throw StatusHelper.argSyntax(Messages.PortReorderCmd_0);
    }
    
    String afterId = null;
    if (cli.hasOption(OPT_AFTER)) {
      IScmCommandLineArgument argAfter = ScmCommandLineArgument.create(cli.getOptionValue(OPT_AFTER), config);
      SubcommandUtil.validateArgument(argAfter, RepoUtil.ItemType.CHANGESET);
      String valAfter = argAfter.getItemSelector();
      UUID uuid = RepoUtil.lookupUuid(valAfter);
      afterId = uuid != null ? uuid.getUuidValue() : valAfter;
    }
    
    if (cli.hasOption(OPT_ORDER)) {
      performReorderAcceptQueue(cli, client, targetWs, repo, componentDto, afterId);
    } else {
      performAddToAcceptQueue(cli, client, targetWs, repo, componentDto, afterId);
    }
  }
  


  private void performReorderAcceptQueue(ICommandLine cli, IFilesystemRestClient client, ParmsWorkspace targetWs, ITeamRepository repo, WorkspaceComponentDTO componentDto, String afterId)
    throws FileSystemException, CLIFileSystemClientException
  {
    List<IScmCommandLineArgument> argSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_ORDER), config);
    SubcommandUtil.validateArgument(argSelectors, RepoUtil.ItemType.CHANGESET);
    List<String> portsToReorder = RepoUtil.getSelectors(argSelectors);
    
    String[] portsArray = new String[portsToReorder.size()];
    
    for (int i = 0; i < portsToReorder.size(); i++) {
      UUID uuid = RepoUtil.lookupUuid((String)portsToReorder.get(i));
      portsArray[i] = (uuid != null ? uuid.getUuidValue() : (String)portsToReorder.get(i));
    }
    
    ParmsReorderAcceptQueue parmsReorder = new ParmsReorderAcceptQueue();
    workspace = targetWs;
    componentItemId = componentDto.getItemId();
    ids = portsArray;
    afterId = afterId;
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    try
    {
      client.postReorderAcceptQueue(parmsReorder, null);
      out.println(Messages.PortReorderCmd_SUCCESS);
      
      if (hasVerboseOption(cli)) {
        printPorts(repo, targetWs, componentDto.getItemId(), client, out, config);
      }
    } catch (TeamRepositoryException tre) {
      throw StatusHelper.wrap(Messages.PortReorderCmd_FAILURE, tre, out);
    }
  }
  


  private void performAddToAcceptQueue(ICommandLine cli, IFilesystemRestClient client, ParmsWorkspace targetWs, ITeamRepository repo, WorkspaceComponentDTO componentDto, String afterId)
    throws FileSystemException, CLIFileSystemClientException
  {
    List<IScmCommandLineArgument> argSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_ADD), config);
    SubcommandUtil.validateArgument(argSelectors, RepoUtil.ItemType.CHANGESET);
    List<String> changeSetsToAdd = RepoUtil.getSelectors(argSelectors);
    
    String[] changeSets = new String[changeSetsToAdd.size()];
    
    for (int i = 0; i < changeSetsToAdd.size(); i++) {
      UUID uuid = RepoUtil.lookupUuid((String)changeSetsToAdd.get(i));
      changeSets[i] = (uuid != null ? uuid.getUuidValue() : (String)changeSetsToAdd.get(i));
    }
    
    ParmsAddToAcceptQueue parmsReorder = new ParmsAddToAcceptQueue();
    workspace = targetWs;
    componentItemId = componentDto.getItemId();
    ids = changeSets;
    afterId = afterId;
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    try
    {
      client.postAddToAcceptQueue(parmsReorder, null);
      out.println(Messages.PortReorderCmd_1);
      
      if (hasVerboseOption(cli)) {
        printPorts(repo, targetWs, componentDto.getItemId(), client, out, config);
      }
    } catch (TeamRepositoryException tre) {
      throw StatusHelper.wrap(Messages.PortReorderCmd_2, tre, out);
    }
  }
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    addVerboseToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.PortCmdOption_WORKSPACE)
      .addOption(OPT_AFTER, Messages.PortReorderCmdOption_AFTER)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_ADD, Messages.PortReorderCmd_3, true)
      .addOption(OPT_ORDER, Messages.PortReorderCmdOption_Ports, true))
      .addOption(CommonOptions.OPT_POSITIONAL_ARG_SEPARATOR, NLS.bind(Messages.PortReorderCmdOption_SEPARATOR, OPT_ORDER.getName()))
      .addOption(OPT_COMPONENT, Messages.PortCmdOption_COMPONENT);
    return options;
  }
  
  protected void validateCommonArguments(ICommandLine cli) throws FileSystemException
  {
    IScmCommandLineArgument workspace = ScmCommandLineArgument.create(cli.getOptionValue(OPT_WORKSPACE, null), config);
    SubcommandUtil.validateArgument(workspace, RepoUtil.ItemType.WORKSPACE);
    
    IScmCommandLineArgument component = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT.getId()), config);
    SubcommandUtil.validateArgument(component, RepoUtil.ItemType.COMPONENT);
  }
}
