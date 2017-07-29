package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.workspace.WorkspacePropertyListCmd;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.SimpleGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import java.util.List;
import org.eclipse.osgi.util.NLS;






public class ShowAttributesCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public ShowAttributesCmd() {}
  
  public static final NamedOptionDefinition OPT_WORKSPACE = new NamedOptionDefinition("w", 
    "workspace", 1, -1, "@");
  public static final NamedOptionDefinition OPT_SNAPSHOT = new NamedOptionDefinition("s", 
    "snapshot", 1, "@");
  public static final NamedOptionDefinition OPT_COMPONENT = new NamedOptionDefinition("C", 
    "component", 1, -1, "@");
  public static final NamedOptionDefinition OPT_BASELINE = new NamedOptionDefinition("b", 
    "baseline", 1, "@");
  public static final NamedOptionDefinition OPT_SNAPSHOT_WORKSPACE = new NamedOptionDefinition(null, 
    "snapshot-workspace", 1);
  public static final NamedOptionDefinition OPT_BASELINE_COMPONENT = new NamedOptionDefinition(null, 
    "baseline-component", 1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_WORKSPACE, Messages.ShowAttributesCmdOptions_WORKSPACE_HELP, true)
      .addOption(new SimpleGroup(true)
      .addOption(OPT_SNAPSHOT, Messages.ShowAttributesCmdOptions_SNAPSHOT_HELP, true)
      .addOption(OPT_SNAPSHOT_WORKSPACE, Messages.SnapshotPropertiesCmdOptions_WORKSPACE_HELP, false))
      .addOption(OPT_COMPONENT, Messages.ShowAttributesCmdOptions_COMPONENT_HELP, true)
      .addOption(new SimpleGroup(true)
      .addOption(OPT_BASELINE, Messages.ShowAttributesCmdOptions_BASELINE_HELP, true)
      .addOption(OPT_BASELINE_COMPONENT, Messages.BaselinePropertiesCmdOptions_COMPONENT_HELP, false)));
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    int count = 0;
    if (cli.hasOption(OPT_WORKSPACE)) count++;
    if (cli.hasOption(OPT_SNAPSHOT)) count++;
    if (cli.hasOption(OPT_COMPONENT)) count++;
    if (cli.hasOption(OPT_BASELINE)) { count++;
    }
    if (count == 0) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_MUST_SPECIFY_1_OF_4_ARGUMENTS, 
        new String[] { OPT_WORKSPACE.getName(), OPT_SNAPSHOT.getName(), 
        OPT_COMPONENT.getName(), OPT_BASELINE.getName() }));
    }
    
    if (count > 1) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_4_ARGUMENTS, 
        new String[] { OPT_WORKSPACE.getName(), OPT_SNAPSHOT.getName(), 
        OPT_COMPONENT.getName(), OPT_BASELINE.getName() }));
    }
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    new IndentingPrintStream(config.getContext().stdout());
    new IndentingPrintStream(config.getContext().stderr());
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    if (cli.hasOption(OPT_WORKSPACE)) {
      List<IScmCommandLineArgument> workspaceSelectorList = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_WORKSPACE), config);
      SubcommandUtil.validateArgument(workspaceSelectorList, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      WorkspacePropertyListCmd.listProperties(workspaceSelectorList, null, null, client, config);
    } else if (cli.hasOption(OPT_SNAPSHOT)) {
      IScmCommandLineArgument ssSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_SNAPSHOT), config);
      SubcommandUtil.validateArgument(ssSelector, RepoUtil.ItemType.SNAPSHOT);
      
      IScmCommandLineArgument wsSelector = null;
      if (cli.hasOption(OPT_SNAPSHOT_WORKSPACE)) {
        wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_SNAPSHOT_WORKSPACE), config);
        SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      }
      
      SnapshotPropertyListCmd.listProperties(ssSelector, wsSelector, null, true, client, config);
    } else if (cli.hasOption(OPT_COMPONENT)) {
      List<IScmCommandLineArgument> componentSelectorList = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_COMPONENT), config);
      SubcommandUtil.validateArgument(componentSelectorList, RepoUtil.ItemType.COMPONENT);
      ComponentPropertyListCmd.listProperties(componentSelectorList, null, null, client, config);
    } else {
      IScmCommandLineArgument blSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_BASELINE), config);
      SubcommandUtil.validateArgument(blSelector, RepoUtil.ItemType.BASELINE);
      
      IScmCommandLineArgument compSelector = null;
      if (cli.hasOption(OPT_BASELINE_COMPONENT)) {
        compSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_BASELINE_COMPONENT), config);
        SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
      }
      
      BaselinePropertyListCmd.listProperties(blSelector, compSelector, null, true, client, config);
    }
  }
}
