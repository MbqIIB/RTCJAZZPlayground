package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.workspace.WorkspaceUnsetCustomAttributeCmd;
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







public class UnsetCustomAttributesCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public UnsetCustomAttributesCmd() {}
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(new PositionalOptionDefinition(SetCustomAttributesCmd.OPT_KEY, "key", 1, 1), Messages.ExtendedPropertyGetCmd_KeyHelp)
      .addOption(new ContinuousGroup()
      .addOption(new PositionalOptionDefinition(SetCustomAttributesCmd.OPT_FILES, "files", 0, -1), Messages.ExtendedPropertyGetCmd_FilesHelp, false)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(ShowCustomAttributesCmd.OPT_FILE, Messages.ExtendedPropertyRemoveCmd_FilesHelp, true)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(ShowCustomAttributesCmd.OPT_STREAM, Messages.UnsetCustomAttributesCmdOptions_STREAM_HELP, true))
      .addOption(new ContinuousGroup()
      .addOption(ShowCustomAttributesCmd.OPT_SNAPSHOT, Messages.UnsetCustomAttributesCmdOptions_SNAPSHOT_HELP, true)
      .addOption(ShowCustomAttributesCmd.OPT_SNAPSHOT_WORKSPACE, Messages.SnapshotPropertiesCmdOptions_WORKSPACE_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(ShowCustomAttributesCmd.OPT_BASELINE, Messages.UnsetCustomAttributesCmdOptions_BASELINE_HELP, true)
      .addOption(ShowCustomAttributesCmd.OPT_BASELINE_COMPONENT, Messages.BaselinePropertiesCmdOptions_COMPONENT_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(ShowCustomAttributesCmd.OPT_COMPONENT, Messages.UnsetCustomAttributesCmdOptions_COMPONENT_HELP, true));
    return options;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    int count = 0;
    if (cli.hasOption(ShowCustomAttributesCmd.OPT_STREAM)) count++;
    if (cli.hasOption(ShowCustomAttributesCmd.OPT_SNAPSHOT)) count++;
    if (cli.hasOption(ShowCustomAttributesCmd.OPT_BASELINE)) count++;
    if (cli.hasOption(ShowCustomAttributesCmd.OPT_COMPONENT)) { count++;
    }
    if (count > 1) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_4_ARGUMENTS, 
        new String[] { ShowCustomAttributesCmd.OPT_WORKSPACE.getName(), ShowCustomAttributesCmd.OPT_SNAPSHOT.getName(), 
        ShowCustomAttributesCmd.OPT_BASELINE.getName(), ShowCustomAttributesCmd.OPT_COMPONENT.getName() }));
    }
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    String[] unsetCustomAttrs = { cli.getOption(SetCustomAttributesCmd.OPT_KEY) };
    
    if (cli.hasOption(ShowCustomAttributesCmd.OPT_STREAM)) {
      List<IScmCommandLineArgument> workspaceSelectorList = ScmCommandLineArgument.createList(cli.getOptionValues(ShowCustomAttributesCmd.OPT_STREAM), config);
      SubcommandUtil.validateArgument(workspaceSelectorList, new RepoUtil.ItemType[] { RepoUtil.ItemType.STREAM });
      
      WorkspaceUnsetCustomAttributeCmd.unsetCustomAttributes(workspaceSelectorList, unsetCustomAttrs, client, config);
    } else if (cli.hasOption(ShowCustomAttributesCmd.OPT_SNAPSHOT)) {
      IScmCommandLineArgument ssSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowCustomAttributesCmd.OPT_SNAPSHOT), config);
      SubcommandUtil.validateArgument(ssSelector, RepoUtil.ItemType.SNAPSHOT);
      
      IScmCommandLineArgument wsSelector = null;
      if (cli.hasOption(ShowCustomAttributesCmd.OPT_SNAPSHOT_WORKSPACE)) {
        wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowCustomAttributesCmd.OPT_SNAPSHOT_WORKSPACE), config);
        SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      }
      
      SnapshotUnsetCustomAttributeCmd.unsetCustomAttributes(ssSelector, wsSelector, unsetCustomAttrs, client, config);
    } else if (cli.hasOption(ShowCustomAttributesCmd.OPT_BASELINE)) {
      IScmCommandLineArgument blSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowCustomAttributesCmd.OPT_BASELINE), config);
      SubcommandUtil.validateArgument(blSelector, RepoUtil.ItemType.BASELINE);
      
      IScmCommandLineArgument compSelector = null;
      if (cli.hasOption(ShowCustomAttributesCmd.OPT_BASELINE_COMPONENT)) {
        compSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowCustomAttributesCmd.OPT_BASELINE_COMPONENT), config);
        SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
      }
      
      BaselineUnsetCustomAttributeCmd.unsetCustomAttributes(blSelector, compSelector, unsetCustomAttrs, client, config);
    } else if (cli.hasOption(ShowCustomAttributesCmd.OPT_COMPONENT)) {
      IScmCommandLineArgument compSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowCustomAttributesCmd.OPT_COMPONENT), config);
      SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
      
      ComponentUnsetCustomAttributeCmd.unsetCustomAttributes(compSelector, unsetCustomAttrs, client, config);
    } else {
      List<String> strPaths = null;
      
      if (cli.hasOption(ShowCustomAttributesCmd.OPT_FILE)) {
        strPaths = cli.getOptions(ShowCustomAttributesCmd.OPT_FILE);
      } else if (cli.hasOption(SetCustomAttributesCmd.OPT_FILES)) {
        strPaths = cli.getOptions(SetCustomAttributesCmd.OPT_FILES);
      }
      else {
        throw StatusHelper.argSyntax(Messages.CustomAttributesCmdOptions_FILE_MISSING);
      }
      
      List<ILocation> paths = SubcommandUtil.makeAbsolutePaths(config, strPaths);
      String key = cli.getOption(SetCustomAttributesCmd.OPT_KEY);
      
      VersionableUnsetCustomAttributesCmd.unsetCustomAttributes(config, key, paths);
    }
  }
}
