package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.workspace.WorkspaceShowCustomAttributesCmd;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.util.List;
import org.eclipse.osgi.util.NLS;











public class ShowCustomAttributesCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public static final NamedOptionDefinition OPT_WORKSPACE = new NamedOptionDefinition("w", 
    "workspace", 1, -1, "@");
  public static final NamedOptionDefinition OPT_STREAM = new NamedOptionDefinition("S", 
    "stream", 1, -1, "@");
  public static final NamedOptionDefinition OPT_SNAPSHOT = new NamedOptionDefinition("s", 
    "snapshot", 1, "@");
  public static final NamedOptionDefinition OPT_FILE = new NamedOptionDefinition("v", 
    "file", 1, -1, "@");
  public static final NamedOptionDefinition OPT_COMPONENT = new NamedOptionDefinition("C", 
    "component", 1, -1, "@");
  public static final NamedOptionDefinition OPT_BASELINE = new NamedOptionDefinition("b", 
    "baseline", 1, "@");
  public static final NamedOptionDefinition OPT_SNAPSHOT_WORKSPACE = new NamedOptionDefinition(null, 
    "snapshot-workspace", 1);
  public static final NamedOptionDefinition OPT_BASELINE_COMPONENT = new NamedOptionDefinition(null, 
    "baseline-component", 1);
  
  public ShowCustomAttributesCmd() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(new ContinuousGroup()
      .addOption(new PositionalOptionDefinition(SetCustomAttributesCmd.OPT_FILES, "files", 0, -1), Messages.ExtendedPropertyListCmd_ListPropertiesHelp, false)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(OPT_FILE, Messages.ExtendedPropertyListCmd_ListPropertiesHelp, true)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(OPT_STREAM, Messages.ShowCustomAttributesCmdOptions_STREAM_HELP, true))
      .addOption(new ContinuousGroup()
      .addOption(OPT_SNAPSHOT, Messages.ShowCustomAttributesCmdOptions_SNAPSHOT_HELP, true)
      .addOption(OPT_SNAPSHOT_WORKSPACE, Messages.SnapshotPropertiesCmdOptions_WORKSPACE_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(OPT_BASELINE, Messages.ShowCustomAttributesCmdOptions_BASELINE_HELP, true)
      .addOption(OPT_BASELINE_COMPONENT, Messages.BaselinePropertiesCmdOptions_COMPONENT_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(OPT_COMPONENT, Messages.ShowCustomAttributesCmdOptions_COMPONENT_HELP, true));
    return options;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    int count = 0;
    if (cli.hasOption(OPT_STREAM)) count++;
    if (cli.hasOption(OPT_SNAPSHOT)) count++;
    if (cli.hasOption(OPT_BASELINE)) count++;
    if (cli.hasOption(OPT_COMPONENT)) { count++;
    }
    if (count > 1) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_4_ARGUMENTS, 
        new String[] { OPT_WORKSPACE.getName(), OPT_SNAPSHOT.getName(), 
        OPT_BASELINE.getName(), OPT_COMPONENT.getName() }));
    }
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    if (cli.hasOption(OPT_STREAM)) {
      List<IScmCommandLineArgument> workspaceSelectorList = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_STREAM), config);
      SubcommandUtil.validateArgument(workspaceSelectorList, new RepoUtil.ItemType[] { RepoUtil.ItemType.STREAM });
      WorkspaceShowCustomAttributesCmd.showCustomAttributes(workspaceSelectorList, null, null, client, config);
    } else if (cli.hasOption(OPT_SNAPSHOT)) {
      IScmCommandLineArgument ssSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_SNAPSHOT), config);
      SubcommandUtil.validateArgument(ssSelector, RepoUtil.ItemType.SNAPSHOT);
      
      IScmCommandLineArgument wsSelector = null;
      if (cli.hasOption(OPT_SNAPSHOT_WORKSPACE)) {
        wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_SNAPSHOT_WORKSPACE), config);
        SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      }
      
      SnapshotShowCustomAttributesCmd.getCustomAttributes(ssSelector, wsSelector, null, true, client, config);
    } else if (cli.hasOption(OPT_BASELINE)) {
      IScmCommandLineArgument blSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_BASELINE), config);
      SubcommandUtil.validateArgument(blSelector, RepoUtil.ItemType.BASELINE);
      
      IScmCommandLineArgument compSelector = null;
      if (cli.hasOption(OPT_BASELINE_COMPONENT)) {
        compSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_BASELINE_COMPONENT), config);
        SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
      }
      
      BaselineShowCustomAttributesCmd.getCustomAttributes(blSelector, compSelector, null, client, config);
    } else if (cli.hasOption(OPT_COMPONENT)) {
      IScmCommandLineArgument compSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT), config);
      SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
      
      ComponentShowCustomAttributesCmd.getCustomAttributes(compSelector, null, client, config);
    } else {
      config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
      
      List<String> strPaths = null;
      
      if (cli.hasOption(OPT_FILE)) {
        strPaths = cli.getOptions(OPT_FILE);
      } else if (cli.hasOption(SetCustomAttributesCmd.OPT_FILES)) {
        strPaths = cli.getOptions(SetCustomAttributesCmd.OPT_FILES);
      }
      else {
        throw StatusHelper.argSyntax(Messages.CustomAttributesCmdOptions_FILE_MISSING);
      }
      
      List<ILocation> paths = SubcommandUtil.makeAbsolutePaths(config, strPaths);
      
      VersionableShowCustomAttributesCmd.showCustomAttributes(paths, null, config);
    }
  }
}
