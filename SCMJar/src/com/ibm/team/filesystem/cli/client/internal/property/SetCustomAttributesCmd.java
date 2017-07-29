package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.workspace.WorkspaceSetCustomAttributeCmd;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.repository.common.LogFactory;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.eclipse.osgi.util.NLS;












public class SetCustomAttributesCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public static final NamedOptionDefinition OPT_KEY = new NamedOptionDefinition(null, "key", 1);
  public static final NamedOptionDefinition OPT_VALUE = new NamedOptionDefinition(null, "value", 1);
  
  public static final NamedOptionDefinition OPT_FILES = new NamedOptionDefinition(null, "files", 1);
  
  public SetCustomAttributesCmd() {}
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    options
      .addOption(new PositionalOptionDefinition(OPT_KEY, "key", 1, 1), Messages.ExtendedPropertySetCmd_KeyHelp)
      .addOption(new PositionalOptionDefinition(OPT_VALUE, "value", 1, 1), Messages.ExtendedPropertySetCmd_ValueHelp)
      .addOption(new ContinuousGroup()
      .addOption(new PositionalOptionDefinition(OPT_FILES, "files", 0, -1), Messages.ExtendedPropertySetCmd_FilesHelp, true)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(ShowCustomAttributesCmd.OPT_FILE, Messages.ExtendedPropertySetCmd_FilesHelp, true)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(ShowCustomAttributesCmd.OPT_STREAM, Messages.SetCustomAttributesCmdOptions_STREAM_HELP, true))
      .addOption(new ContinuousGroup()
      .addOption(ShowCustomAttributesCmd.OPT_SNAPSHOT, Messages.SetCustomAttributesCmdOptions_SNAPSHOT_HELP, true)
      .addOption(ShowCustomAttributesCmd.OPT_SNAPSHOT_WORKSPACE, Messages.SnapshotPropertiesCmdOptions_WORKSPACE_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(ShowCustomAttributesCmd.OPT_BASELINE, Messages.SetCustomAttributesCmdOptions_BASELINE_HELP, true)
      .addOption(ShowCustomAttributesCmd.OPT_BASELINE_COMPONENT, Messages.BaselinePropertiesCmdOptions_COMPONENT_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(ShowCustomAttributesCmd.OPT_COMPONENT, Messages.SetCustomAttributesCmdOptions_COMPONENT_HELP, true));
    
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
    
    String key = cli.getOption(OPT_KEY);
    String value = cli.getOption(OPT_VALUE);
    try {
      if (cli.hasOption(ShowCustomAttributesCmd.OPT_STREAM)) {
        List<IScmCommandLineArgument> wsSelectorList = ScmCommandLineArgument.createList(cli.getOptionValues(ShowCustomAttributesCmd.OPT_STREAM), config);
        SubcommandUtil.validateArgument(wsSelectorList, new RepoUtil.ItemType[] { RepoUtil.ItemType.STREAM });
        
        Map<String, String> customAttrs = new HashMap();
        customAttrs.put(key, value);
        
        WorkspaceSetCustomAttributeCmd.setCustomAttributes(wsSelectorList, customAttrs, client, config);
      } else if (cli.hasOption(ShowCustomAttributesCmd.OPT_SNAPSHOT)) {
        IScmCommandLineArgument ssSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowCustomAttributesCmd.OPT_SNAPSHOT), config);
        SubcommandUtil.validateArgument(ssSelector, RepoUtil.ItemType.SNAPSHOT);
        
        IScmCommandLineArgument wsSelector = null;
        if (cli.hasOption(ShowCustomAttributesCmd.OPT_SNAPSHOT_WORKSPACE)) {
          wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowCustomAttributesCmd.OPT_SNAPSHOT_WORKSPACE), config);
          SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
        }
        
        Map<String, String> customAttrs = new HashMap();
        customAttrs.put(key, value);
        
        SnapshotSetCustomAttributeCmd.setProperties(ssSelector, wsSelector, customAttrs, client, config);
      } else if (cli.hasOption(ShowCustomAttributesCmd.OPT_BASELINE)) {
        IScmCommandLineArgument blSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowCustomAttributesCmd.OPT_BASELINE), config);
        SubcommandUtil.validateArgument(blSelector, RepoUtil.ItemType.BASELINE);
        
        IScmCommandLineArgument compSelector = null;
        if (cli.hasOption(ShowCustomAttributesCmd.OPT_BASELINE_COMPONENT)) {
          compSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowCustomAttributesCmd.OPT_BASELINE_COMPONENT), config);
          SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
        }
        
        Map<String, String> customAttrs = new HashMap();
        customAttrs.put(key, value);
        
        BaselineSetCustomAttributeCmd.setCustomAttributes(blSelector, compSelector, customAttrs, client, config);
      } else if (cli.hasOption(ShowCustomAttributesCmd.OPT_COMPONENT)) {
        IScmCommandLineArgument compSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowCustomAttributesCmd.OPT_COMPONENT), config);
        SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
        
        Map<String, String> customAttrs = new HashMap();
        customAttrs.put(key, value);
        
        ComponentSetCustomAttributeCmd.setCustomAttributes(compSelector, customAttrs, client, config);
      } else {
        List<String> strPaths = null;
        
        if (cli.hasOption(ShowCustomAttributesCmd.OPT_FILE)) {
          strPaths = cli.getOptions(ShowCustomAttributesCmd.OPT_FILE);
        } else if (cli.hasOption(OPT_FILES)) {
          strPaths = cli.getOptions(OPT_FILES);
        }
        else {
          throw StatusHelper.argSyntax(Messages.CustomAttributesCmdOptions_FILE_MISSING);
        }
        
        List<ILocation> paths = SubcommandUtil.makeAbsolutePaths(config, strPaths);
        
        client = SubcommandUtil.setupDaemon(config);
        
        List<ResourcePropertiesDTO> resPropList = RepoUtil.getResourceProperties(strPaths, 
          SubcommandUtil.shouldRefreshFileSystem(config), client, config, false);
        for (ResourcePropertiesDTO resProp : resPropList) {
          if (resProp.getItemId() == null) {
            throw StatusHelper.disallowed(NLS.bind(Messages.PropertyListCmd_PathDoesNotExist, 
              resProp.getFullPath()));
          }
        }
        
        VersionableSetCustomAttributesCmd.setProperty(config, key, value, client, paths);
      }
    }
    catch (FileSystemException e) {
      throw e;
    } catch (TeamRepositoryException e) {
      String newLine = System.getProperty("line.separator");
      Log log = LogFactory.getLog(VersionableSetCustomAttributesCmd.class.getName());
      log.error(e);
      String msg = e.getMessage();
      if (msg == null) {
        msg = Messages.ExtendedPropertySetCmd_CouldNotSetProperties + newLine + Messages.ERROR_CHECK_LOG;
      } else {
        msg = msg + newLine + Messages.ExtendedPropertySetCmd_CouldNotSetProperties + newLine + Messages.ERROR_CHECK_LOG;
      }
      throw StatusHelper.failure(msg, null);
    }
  }
}
