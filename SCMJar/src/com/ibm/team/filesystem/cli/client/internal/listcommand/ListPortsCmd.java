package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.changeset.ChangesetCommonOptions;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.PortsUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsIndexedPageDescriptor;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.ChoppingIndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.eclipse.osgi.util.NLS;




public class ListPortsCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public static final PositionalOptionDefinition OPT_COMPONENT_SELECTOR = new PositionalOptionDefinition("component", 1, 1);
  public static final NamedOptionDefinition OPT_CURRENT = new NamedOptionDefinition("c", "current", 0);
  public static final NamedOptionDefinition OPT_UNRESOLVED = new NamedOptionDefinition("U", "unresolved", 0);
  public static final NamedOptionDefinition OPT_WIDE = new NamedOptionDefinition("W", "wide", 0);
  
  public ListPortsCmd() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(OPT_WIDE, CommonOptions.OPT_WIDE_HELP)
      .addOption(CommonOptions.OPT_MAXRESULTS, CommonOptions.OPT_MAXRESULTS_HELP)
      .addOption(OPT_CURRENT, NLS.bind(Messages.ListPortsCmdOption_Current, OPT_UNRESOLVED.getName()))
      .addOption(OPT_UNRESOLVED, Messages.ListPortsCmdOption_Unresolved)
      .addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.PortCmdOption_WORKSPACE)
      .addOption(OPT_COMPONENT_SELECTOR, Messages.ListPortsCmd_Component);
    
    return options;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_WORKSPACE_NAME.getId(), null), config);
    SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.WORKSPACE);
    
    ParmsWorkspace targetWs = new ParmsWorkspace();
    ITeamRepository repo = null;
    
    if (wsSelector == null) {
      List<ISandboxWorkspace> wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, config);
      if (wsInSandboxList.size() != 1) {
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.PortCmdOption_AmbiguousWorkspace, 
          cli.getDefinition().getOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME.getId()).getName()));
      }
      
      ISandboxWorkspace wsInSandbox = (ISandboxWorkspace)wsInSandboxList.iterator().next();
      repositoryUrl = RepoUtil.getRepoUri(config, client, wsInSandbox.getRepositoryId(), 
        Collections.singletonList(wsInSandbox));
      workspaceItemId = wsInSandbox.getWorkspaceItemId();
      
      repo = RepoUtil.login(config, client, config.getConnectionInfo(repositoryUrl));
    }
    else {
      repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      
      IWorkspace ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, false, repo, config);
      
      repositoryUrl = repo.getRepositoryURI();
      workspaceItemId = ws.getItemId().getUuidValue();
    }
    
    IScmCommandLineArgument compSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT_SELECTOR), config);
    SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
    IComponent comp = RepoUtil.getComponent(compSelector.getItemSelector(), repo, config);
    
    if (comp == null) {
      throw StatusHelper.argSyntax(Messages.Common_UNIQUE_LOADED_COMP);
    }
    
    boolean wide = (cli.hasOption(OPT_WIDE)) || (config.isJSONEnabled());
    int chopsize = SubcommandUtil.getTerminalWidth(config);
    PrintStream ps = config.getContext().stdout();
    IndentingPrintStream out = wide ? new IndentingPrintStream(ps) : new ChoppingIndentingPrintStream(ps, chopsize);
    
    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    options.enablePrinter(4);
    options.enablePrinter(22);
    if (cli.hasOption(OPT_CURRENT)) {
      options.enablePrinter(29);
    }
    options.enablePrinter(30);
    
    options.enablePrinter(32);
    if (cli.hasOption(OPT_UNRESOLVED)) {
      options.enablePrinter(33);
    }
    
    ParmsIndexedPageDescriptor parmsPageDescriptor = PortsUtil.printPorts(targetWs, comp.getItemId().getUuidValue(), 
      RepoUtil.getMaxResultsOption(cli), client, options, out, config);
    
    if ((parmsPageDescriptor != null) && (parmsPageDescriptor.hasNextPage()) && (!cli.hasOption(CommonOptions.OPT_MAXRESULTS)) && (!config.isJSONEnabled())) {
      config.getContext().stdout().println(NLS.bind(Messages.ListPortsCmd_MORE_ITEMS_AVAILABLE, 
        totalSize, cli.getDefinition().getOption(CommonOptions.OPT_MAXRESULTS).getName()));
    }
  }
}
