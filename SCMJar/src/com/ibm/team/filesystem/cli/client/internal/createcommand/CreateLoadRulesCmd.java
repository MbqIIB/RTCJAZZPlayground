package com.ibm.team.filesystem.cli.client.internal.createcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptResultDisplayer;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil.ItemInfo;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBackupDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLoadRuleSerializationInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsOutOfSyncInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxLoadRules;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.SandboxUpdateDilemmaDTO;
import com.ibm.team.filesystem.common.internal.rest.client.load.SandboxLoadRulesResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.osgi.util.NLS;



public class CreateLoadRulesCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public CreateLoadRulesCmd() {}
  
  static final IPositionalOptionDefinition OPT_RESULTFOLDER_PATH = new PositionalOptionDefinition("rulesfolder-path", 1, 1);
  static final NamedOptionDefinition OPT_WORKSPACE_NAME = new NamedOptionDefinition("w", "workspace", 1);
  static final NamedOptionDefinition OPT_COMPONENT_NAME = new NamedOptionDefinition("C", "component", 1);
  static final NamedOptionDefinition OPT_COMMENTS = new NamedOptionDefinition("c", "comment", 1);
  static final NamedOptionDefinition OPT_FILENAME = new NamedOptionDefinition("f", "filename", 1);
  static final NamedOptionDefinition OPT_USE_COMPONENT_NAME = new NamedOptionDefinition(null, "use-component-name", 0);
  static final NamedOptionDefinition OPT_USE_REPOSITORY_PATH = new NamedOptionDefinition(null, "use-repository-path", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_DIRECTORY, "d", "directory", Messages.CreateLoadRulesCmdOptions_SANDBOX_PATH, 1)
      .addOption(OPT_WORKSPACE_NAME, Messages.CreateLoadRulesCmdOptions_WS)
      .addOption(OPT_COMPONENT_NAME, Messages.CreateLoadRulesCmdOptions_COMP)
      .addOption(OPT_COMMENTS, Messages.CreateLoadRulesCmdOptions_COMMENTS)
      .addOption(OPT_FILENAME, Messages.CreateLoadRulesCmdOptions_PREF_FILENAME)
      .addOption(OPT_USE_COMPONENT_NAME, Messages.CreateLoadRulesCmdOptions_USE_COMPONENT_NAME)
      .addOption(OPT_USE_REPOSITORY_PATH, Messages.CreateLoadRulesCmdOptions_USE_REPOSITORY_PATH)
      .addOption(OPT_RESULTFOLDER_PATH, Messages.CreateLoadRulesCmdOptions_RESULT_PATH);
    

    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    

    File resultFolderPath = SubcommandUtil.canonicalize(new File(cli.getOption(OPT_RESULTFOLDER_PATH.getId())));
    if ((resultFolderPath.exists()) && (!resultFolderPath.isDirectory())) {
      throw StatusHelper.argSyntax(Messages.CreateLoadRulesCmd_INVALID_RESUTLFOLDER_PATH);
    }
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    

    ParmsWorkspace ws = null;
    if (cli.hasOption(OPT_WORKSPACE_NAME)) {
      IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_WORKSPACE_NAME, null), config);
      SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.WORKSPACE);
      ITeamRepository repo = null;
      if (wsSelector.getRepositorySelector() != null) {
        repo = RepoUtil.login(config, client, 
          config.getConnectionInfo(wsSelector.getRepositorySelector()));
      }
      ws = RepoUtil.findWorkspaceInSandbox(wsSelector.getItemSelector(), 
        repo != null ? repo.getId() : null, client, config);
    }
    

    ShareDTO compShare = null;
    IUuidAliasRegistry.IUuidAlias compAlias; if (cli.hasOption(OPT_COMPONENT_NAME)) {
      IScmCommandLineArgument compSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT_NAME, null), config);
      
      String wsId = ws != null ? workspaceItemId : null;
      List<ShareDTO> shareList = RepoUtil.getSharesInSandbox(wsId, client, config);
      compAlias = RepoUtil.lookupUuidAndAlias(compSelector.getItemSelector());
      
      Map<String, ShareDTO> compToShare = new HashMap();
      for (ShareDTO share : shareList) {
        if (((compAlias != null) && (compAlias.getUuid().getUuidValue().equals(share.getComponentItemId()))) || (
          (compSelector.getItemSelector().equals(share.getComponentName())) && 
          (!compToShare.keySet().contains(share.getComponentItemId())))) {
          compToShare.put(share.getComponentItemId(), share);
        }
      }
      

      if (compToShare.size() == 0)
        throw StatusHelper.argSyntax(NLS.bind(Messages.Common_COMP_NOT_FOUND, compSelector.getItemSelector()));
      if (compToShare.size() > 1) {
        List<SubcommandUtil.ItemInfo> compsMatched = new ArrayList(compToShare.size());
        for (ShareDTO comp : compToShare.values()) {
          compsMatched.add(new SubcommandUtil.ItemInfo(comp.getComponentName(), comp.getComponentItemId(), RepoUtil.getRepoUri(config, client, comp), RepoUtil.ItemType.COMPONENT));
        }
        
        SubcommandUtil.displayAmbiguousSelectorException(compSelector.getItemSelector(), compsMatched, config);
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.Common_AMBIGUOUS_COMPONENT, compSelector.getItemSelector()));
      }
      
      compShare = (ShareDTO)compToShare.values().iterator().next();
      ws = new ParmsWorkspace(RepoUtil.getRepoUri(config, client, compShare), compShare.getContextItemId());
    }
    

    if (ws != null) {
      RepoUtil.login(config, client, config.getConnectionInfo(repositoryUrl));
    }
    else {
      List<ISandboxWorkspace> wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, config);
      if (wsInSandboxList.size() == 0) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.Common_NOT_A_SANDBOX, config.getContext().getCurrentWorkingDirectory()));
      }
      
      List<String> repoIds = new ArrayList();
      
      for (ISandboxWorkspace wsFound : wsInSandboxList) {
        if (!repoIds.contains(wsFound.getRepositoryId())) {
          String repoUri = RepoUtil.getRepoUri(config, client, wsFound.getRepositoryId(), 
            Collections.singletonList(wsFound));
          RepoUtil.login(config, client, config.getConnectionInfo(repoUri));
          repoIds.add(wsFound.getRepositoryId());
        }
      }
    }
    

    ParmsSandboxLoadRules parms = new ParmsSandboxLoadRules();
    sandboxPath = config.getCurrentWorkingDirectory().getAbsolutePath();
    
    outOfSyncInstructions = new ParmsOutOfSyncInstructions();
    outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
    outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
    
    sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
    
    serializationInstructions = new ParmsLoadRuleSerializationInstructions();
    serializationInstructions.resultFolder = resultFolderPath.getAbsolutePath();
    if (cli.hasOption(OPT_FILENAME)) {
      serializationInstructions.preferredFileName = cli.getOption(OPT_FILENAME);
    } else {
      ILocation sandboxLoc = new PathLocation(sandboxPath);
      if (sandboxLoc.getName().length() == 0) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.CreateLoadRulesCmd_SPECIFY_FILENAME, 
          cli.getDefinition().getOption(OPT_FILENAME).getName()));
      }
      serializationInstructions.preferredFileName = sandboxLoc.getName();
    }
    if (cli.hasOption(OPT_COMMENTS)) {
      serializationInstructions.comments = new String[1];
      serializationInstructions.comments[0] = cli.getOption(OPT_COMMENTS);
    }
    serializationInstructions.useComponentName = Boolean.valueOf(cli.hasOption(OPT_USE_COMPONENT_NAME));
    serializationInstructions.useRepositoryPath = Boolean.valueOf(cli.hasOption(OPT_USE_REPOSITORY_PATH));
    if (ws != null) {
      workspace = ws;
    }
    if (compShare != null) {
      componentItemId = compShare.getComponentItemId();
    }
    

    SandboxLoadRulesResultDTO result = null;
    try {
      result = client.getSandboxLoadRules(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.CreateLoadRulesCmd_FAILURE, e, new IndentingPrintStream(config.getContext().stderr()), null);
    }
    
    if ((result.isCancelled()) && 
      (result.getOutOfSyncShares().size() > 0)) {
      AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
    }
    

    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    if (result.getSandboxUpdateDilemma().getBackedUpToShed().size() > 0) {
      SubcommandUtil.showShedUpdate(Messages.AcceptResultDisplayer_SHED_MESSAGE, out, result.getSandboxUpdateDilemma().getBackedUpToShed());
    }
    
    out.println(Messages.CreateLoadRulesCmd_SUCCESS);
  }
}
