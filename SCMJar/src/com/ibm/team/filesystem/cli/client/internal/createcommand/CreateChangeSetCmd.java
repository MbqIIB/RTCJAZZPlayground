package com.ibm.team.filesystem.cli.client.internal.createcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.changeset.ChangesetCommonOptions;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCreateChangeSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.CreateResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import java.util.Collections;
import java.util.List;
import org.eclipse.osgi.util.NLS;




public class CreateChangeSetCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public CreateChangeSetCmd() {}
  
  static final NamedOptionDefinition OPT_COMPONENT_NAME = new NamedOptionDefinition(null, "component", 1, "@");
  
  static final NamedOptionDefinition OPT_NO_CURRENT = new NamedOptionDefinition("n", "no-current", 0);
  
  static final PositionalOptionDefinition OPT_COMMENT = new PositionalOptionDefinition("comment", 0, 1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options opts = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(opts);
    
    opts
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(OPT_NO_CURRENT, Messages.CreateChangeSetCmd_11)
      .addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.CreateChangeSetCmd_0)
      .addOption(OPT_COMPONENT_NAME, Messages.CreateChangeSetCmd_1)
      .addOption(OPT_COMMENT, Messages.CreateChangeSetCmd_10);
    
    return opts;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_WORKSPACE_NAME.getId(), null), config);
    SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.WORKSPACE);
    IScmCommandLineArgument compSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT_NAME, null), config);
    SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
    

    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ParmsWorkspace ws = null;
    ITeamRepository repo = null;
    
    if (wsSelector != null) {
      ws = RepoUtil.findWorkspaceAndLogin(wsSelector, client, config);
      repo = RepoUtil.getSharedRepository(repositoryUrl, true);
      

      if (compSelector != null) {
        RepoUtil.validateItemRepos(RepoUtil.ItemType.CHANGESET, Collections.singletonList(compSelector), repo, config);
      }
    } else if (compSelector != null) {
      repo = RepoUtil.loginUrlArgAncestor(config, client, compSelector);
      ws = RepoUtil.findWorkspaceInSandbox(null, repo.getId(), client, config);
    } else {
      ws = RepoUtil.findWorkspaceInSandbox(null, null, client, config);
      repo = RepoUtil.login(config, client, config.getConnectionInfo(repositoryUrl));
    }
    

    WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
    WorkspaceComponentDTO component = findComponent(wsDetails, compSelector, client, config);
    

    ParmsCreateChangeSet parms = new ParmsCreateChangeSet();
    workspace = ws;
    componentItemId = component.getItemId();
    comment = cli.getOption(OPT_COMMENT, "");
    current = Boolean.valueOf(!cli.hasOption(OPT_NO_CURRENT));
    
    CreateResultDTO result = null;
    try {
      result = client.postCreateChangeSet(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.CreateChangeSetCmd_8, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
    
    JSONObject jObj = JSONPrintUtil.jsonizeResult(Messages.CreateChangeSetCmd_9, result.getChangeSetItemId(), repositoryUrl, RepoUtil.ItemType.CHANGESET);
    
    PendingChangesUtil.printSuccess(jObj, config);
  }
  
  private WorkspaceComponentDTO findComponent(WorkspaceDetailsDTO wsDetails, IScmCommandLineArgument compSelector, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    WorkspaceComponentDTO component = null;
    

    String compToFind = null;
    if (compSelector == null)
    {
      if (wsDetails.getComponents().size() == 1) {
        compToFind = ((WorkspaceComponentDTO)wsDetails.getComponents().get(0)).getItemId();
      } else {
        List<ShareDTO> shareList = RepoUtil.getSharesInSandbox(wsDetails.getItemId(), client, config);
        if (shareList.size() != 1) {
          throw StatusHelper.ambiguousSelector(Messages.CreateChangeSetCmd_3);
        }
        compToFind = ((ShareDTO)shareList.get(0)).getComponentItemId();
      }
    } else {
      compToFind = compSelector.getItemSelector();
    }
    


    IUuidAliasRegistry.IUuidAlias compAlias = RepoUtil.lookupUuidAndAlias(compToFind);
    for (WorkspaceComponentDTO comp : wsDetails.getComponents()) {
      if (((compAlias != null) && (compAlias.getUuid().getUuidValue().equals(comp.getItemId()))) || 
        (compToFind.equals(comp.getName()))) {
        component = comp;
        break;
      }
    }
    
    if (component == null) {
      if (compSelector == null) {
        throw StatusHelper.ambiguousSelector(Messages.CreateChangeSetCmd_3);
      }
      throw StatusHelper.itemNotFound(NLS.bind(Messages.Common_COMP_NOT_FOUND, compToFind));
    }
    

    return component;
  }
}
