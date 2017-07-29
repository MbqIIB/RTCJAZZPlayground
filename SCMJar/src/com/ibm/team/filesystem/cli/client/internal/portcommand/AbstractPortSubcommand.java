package com.ibm.team.filesystem.cli.client.internal.portcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.changeset.ChangesetCommonOptions;
import com.ibm.team.filesystem.cli.client.internal.listcommand.ListPortsCmd;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.PortsUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsIndexedPageDescriptor;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import java.io.PrintStream;
import org.eclipse.osgi.util.NLS;



public abstract class AbstractPortSubcommand
  extends AbstractSubcommand
  implements IOptionSource
{
  public AbstractPortSubcommand() {}
  
  protected static final IOptionKey OPT_WORKSPACE = ChangesetCommonOptions.OPT_WORKSPACE_NAME.getId();
  protected static final PositionalOptionDefinition OPT_COMPONENT = new PositionalOptionDefinition("component", 1, 1);
  protected static final NamedOptionDefinition OPT_VERBOSE = new NamedOptionDefinition("v", "verbose", 0);
  
  protected void validateCommonArguments(ICommandLine cli) throws FileSystemException {
    IScmCommandLineArgument workspace = ScmCommandLineArgument.create(cli.getOptionValue(OPT_WORKSPACE, null), config);
    SubcommandUtil.validateArgument(workspace, RepoUtil.ItemType.WORKSPACE);
    
    IScmCommandLineArgument component = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT.getId()), config);
    SubcommandUtil.validateArgument(component, RepoUtil.ItemType.COMPONENT);
  }
  
  public static void printPorts(ITeamRepository repo, ParmsWorkspace workspace, String componentId, IFilesystemRestClient client, IndentingPrintStream out, IScmClientConfiguration config) throws FileSystemException
  {
    printPorts(workspace, componentId, client, out, config);
  }
  
  public static void printPorts(ParmsWorkspace workspace, String componentId, IFilesystemRestClient client, IndentingPrintStream out, IScmClientConfiguration config) throws FileSystemException
  {
    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    options.enablePrinter(4);
    options.enablePrinter(22);
    options.enablePrinter(30);
    options.enablePrinter(32);
    
    out.println();
    
    ICommandLine cli = config.getSubcommandCommandLine();
    ParmsIndexedPageDescriptor parmsPageDescriptor = PortsUtil.printPorts(workspace, componentId, RepoUtil.getMaxResultsOption(cli), client, options, out, config);
    
    if ((parmsPageDescriptor != null) && (parmsPageDescriptor.hasNextPage()) && (!cli.hasOption(CommonOptions.OPT_MAXRESULTS)) && (!config.isJSONEnabled())) {
      ISubcommandDefinition defnTemp = SubcommandUtil.getClassSubCommandDefn(config, 
        ListPortsCmd.class);
      config.getContext().stdout().println(NLS.bind(Messages.AbstractPortSubCmd_MORE_ITEMS_AVAILABLE, new String[] { Long.toString(totalSize.longValue()), 
        config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp).toString(), CommonOptions.OPT_MAXRESULTS.getName() }));
    }
  }
  
  protected void addVerboseToOptions(Options options) throws ConflictingOptionException {
    options.addOption(OPT_VERBOSE, Messages.PortCmdOption_VERBOSE);
  }
  
  protected boolean hasVerboseOption(ICommandLine cli) {
    return cli.hasOption(OPT_VERBOSE);
  }
}
