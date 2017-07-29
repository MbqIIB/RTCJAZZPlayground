package com.ibm.team.filesystem.cli.client.internal.createcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsComponentChange;
import com.ibm.team.filesystem.client.rest.parameters.ParmsConfigurationChanges;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPutWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.ConfigurationDescriptorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.workspace.PutWorkspaceResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.util.List;
import org.eclipse.osgi.util.NLS;







public class CreateComponentCommand
  extends AbstractSubcommand
  implements IOptionSource
{
  public CreateComponentCommand() {}
  
  public static final IOptionKey OPT_NAME = new OptionKey("name");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(new PositionalOptionDefinition(OPT_NAME, "name", 1, 1), Messages.CreateComponentCommand_NameForTheComponent)
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@"), Messages.CreateComponentCommand_WorkspaceToAddComponent);
    

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
    IWorkspace ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    
    String name = cli.getOption(OPT_NAME);
    
    UUID componentItemId = null;
    try {
      ParmsPutWorkspace parms = new ParmsPutWorkspace();
      workspace = new ParmsWorkspace();
      workspace.repositoryUrl = repo.getRepositoryURI();
      workspace.workspaceItemId = ws.getItemId().getUuidValue();
      configurationChanges = new ParmsConfigurationChanges();
      configurationChanges.components = new ParmsComponentChange[] { new ParmsComponentChange() };
      configurationChanges.components[0].cmd = "addComponent";
      configurationChanges.components[0].name = name;
      PutWorkspaceResultDTO result = client.postPutWorkspace(parms, null);
      if (!result.isSetComponentsAdded()) {
        throw StatusHelper.failure(NLS.bind(Messages.CreateComponentCommand_CouldNotCreateComponent, name), null);
      }
      List<ConfigurationDescriptorDTO> componentsAdded = result.getComponentsAdded();
      if (componentsAdded.size() != 1) {
        throw StatusHelper.failure(NLS.bind(Messages.CreateComponentCommand_CouldNotCreateComponent, name), null);
      }
      ConfigurationDescriptorDTO componentDTO = (ConfigurationDescriptorDTO)componentsAdded.get(0);
      componentItemId = UUID.valueOf(componentDTO.getComponentItemId());
    }
    catch (TeamRepositoryException e)
    {
      throw StatusHelper.wrap(NLS.bind(Messages.CreateComponentCommand_CouldNotCreateComponent, name), e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    
    JSONObject comp = JSONPrintUtil.jsonizeResult(Messages.CreateComponentCommand_ComponentSuccessfullyCreated, name, componentItemId.getUuidValue(), repo.getRepositoryURI(), RepoUtil.ItemType.COMPONENT);
    
    PendingChangesUtil.printSuccess(comp, config);
  }
}
