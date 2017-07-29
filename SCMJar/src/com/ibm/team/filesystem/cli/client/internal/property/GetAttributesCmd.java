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
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.SimpleGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.osgi.util.NLS;








public class GetAttributesCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public static final NamedOptionDefinition OPT_NAME = new NamedOptionDefinition(null, "name", 0);
  public static final NamedOptionDefinition OPT_DESCRIPTION = new NamedOptionDefinition(null, "description", 0);
  public static final NamedOptionDefinition OPT_OWNEDBY = new NamedOptionDefinition(null, "ownedby", 0);
  public static final NamedOptionDefinition OPT_VISIBILITY = new NamedOptionDefinition(null, "visibility", 0);
  public static final NamedOptionDefinition OPT_AUTO_FILE_LOCK_PATTTERN = new NamedOptionDefinition(null, "auto-lock-files", 0);
  public static final NamedOptionDefinition OPT_ITERATION = new NamedOptionDefinition(null, "iteration", 0);
  public static final NamedOptionDefinition OPT_RELEASE = new NamedOptionDefinition(null, "release", 0);
  
  public GetAttributesCmd() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(new ContinuousGroup()
      .addOption(ShowAttributesCmd.OPT_WORKSPACE, Messages.GetAttributesCmdOptions_WORKSPACE_HELP, true)
      .addOption(new SimpleGroup(true)
      .addOption(OPT_NAME, Messages.SetAttributesCmdOptions_NAME_HELP, false)
      .addOption(OPT_DESCRIPTION, Messages.SetAttributesCmdOptions_DESCRIPTION_HELP, false)
      .addOption(OPT_OWNEDBY, Messages.SetAttributesCmdOptions_OWNEDBY_HELP, false)
      .addOption(OPT_VISIBILITY, Messages.GetAttributesCmdOptions_VISIBILITY_HELP, false)
      .addOption(OPT_AUTO_FILE_LOCK_PATTTERN, Messages.GetAttributesCmdOptions_AUTO_FILE_LOCK_PATTERN_HELP, false)))
      .addOption(new ContinuousGroup()
      .addOption(ShowAttributesCmd.OPT_SNAPSHOT, Messages.GetAttributesCmdOptions_SNAPSHOT_HELP, true)
      .addOption(ShowAttributesCmd.OPT_SNAPSHOT_WORKSPACE, Messages.SnapshotPropertiesCmdOptions_WORKSPACE_HELP, false)
      .addOption(new SimpleGroup(true)
      .addOption(OPT_NAME, Messages.SetAttributesCmdOptions_NAME_HELP, false)
      .addOption(OPT_DESCRIPTION, Messages.SetAttributesCmdOptions_DESCRIPTION_HELP, false)
      .addOption(OPT_ITERATION, Messages.GetAttributesCmdOptions_ITERATION_HELP, false)
      .addOption(OPT_RELEASE, Messages.GetAttributesCmdOptions_RELEASE_HELP, false)
      .addOption(OPT_OWNEDBY, Messages.SetAttributesCmdOptions_OWNEDBY_HELP, false)))
      .addOption(new ContinuousGroup()
      .addOption(ShowAttributesCmd.OPT_COMPONENT, Messages.GetAttributesCmdOptions_COMPONENT_HELP, true)
      .addOption(new SimpleGroup(true)
      .addOption(OPT_NAME, Messages.SetAttributesCmdOptions_NAME_HELP, false)
      .addOption(OPT_OWNEDBY, Messages.SetAttributesCmdOptions_OWNEDBY_HELP, false)
      .addOption(OPT_VISIBILITY, Messages.GetAttributesCmdOptions_VISIBILITY_HELP, false)))
      .addOption(new ContinuousGroup()
      .addOption(ShowAttributesCmd.OPT_BASELINE, Messages.GetAttributesCmdOptions_BASELINE_HELP, true)
      .addOption(ShowAttributesCmd.OPT_BASELINE_COMPONENT, Messages.BaselinePropertiesCmdOptions_COMPONENT_HELP, false)
      .addOption(new SimpleGroup(true)
      .addOption(OPT_NAME, Messages.SetAttributesCmdOptions_NAME_HELP, false)
      .addOption(OPT_DESCRIPTION, Messages.SetAttributesCmdOptions_DESCRIPTION_HELP, false)));
    return options;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    int count = 0;
    if (cli.hasOption(ShowAttributesCmd.OPT_WORKSPACE)) count++;
    if (cli.hasOption(ShowAttributesCmd.OPT_SNAPSHOT)) count++;
    if (cli.hasOption(ShowAttributesCmd.OPT_COMPONENT)) count++;
    if (cli.hasOption(ShowAttributesCmd.OPT_BASELINE)) { count++;
    }
    if (count == 0) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_MUST_SPECIFY_1_OF_4_ARGUMENTS, 
        new String[] { ShowAttributesCmd.OPT_WORKSPACE.getName(), ShowAttributesCmd.OPT_SNAPSHOT.getName(), 
        ShowAttributesCmd.OPT_COMPONENT.getName(), ShowAttributesCmd.OPT_BASELINE.getName() }));
    }
    
    if (count > 1) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_4_ARGUMENTS, 
        new String[] { ShowAttributesCmd.OPT_WORKSPACE.getName(), ShowAttributesCmd.OPT_SNAPSHOT.getName(), 
        ShowAttributesCmd.OPT_COMPONENT.getName(), ShowAttributesCmd.OPT_BASELINE.getName() }));
    }
    
    if ((!cli.hasOption(ShowAttributesCmd.OPT_WORKSPACE)) && (cli.hasOption(OPT_AUTO_FILE_LOCK_PATTTERN))) {
      throw StatusHelper.propertiesUnavailable(NLS.bind(Messages.Stream_ONLY_OPTION, OPT_AUTO_FILE_LOCK_PATTTERN.getName()));
    }
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    new IndentingPrintStream(config.getContext().stdout());
    new IndentingPrintStream(config.getContext().stderr());
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    if (cli.hasOption(ShowAttributesCmd.OPT_WORKSPACE)) {
      if ((!cli.hasOption(OPT_NAME)) && (!cli.hasOption(OPT_DESCRIPTION)) && 
        (!cli.hasOption(OPT_OWNEDBY)) && (!cli.hasOption(OPT_VISIBILITY)) && (!cli.hasOption(OPT_AUTO_FILE_LOCK_PATTTERN))) {
        throw StatusHelper.argSyntax(
        
          NLS.bind(Messages.Common_MUST_SPECIFY_1_OF_4_ARGUMENTS, new String[] { OPT_NAME.getName(), OPT_DESCRIPTION.getName(), OPT_OWNEDBY.getName(), OPT_VISIBILITY.getName() }) + " " + NLS.bind(Messages.Stream_MUST_SPECIFY_ARGUMENT, OPT_AUTO_FILE_LOCK_PATTTERN.getName()));
      }
      

      List<IScmCommandLineArgument> workspaceSelectorList = ScmCommandLineArgument.createList(cli.getOptionValues(ShowAttributesCmd.OPT_WORKSPACE), config);
      SubcommandUtil.validateArgument(workspaceSelectorList, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      
      List<String> propertyNames = new ArrayList();
      if (cli.hasOption(OPT_NAME)) {
        propertyNames.add("name");
      }
      if (cli.hasOption(OPT_DESCRIPTION)) {
        propertyNames.add("description");
      }
      if (cli.hasOption(OPT_OWNEDBY)) {
        propertyNames.add("ownedby");
      }
      if (cli.hasOption(OPT_VISIBILITY)) {
        propertyNames.add("visibility");
      }
      if (cli.hasOption(OPT_AUTO_FILE_LOCK_PATTTERN)) {
        propertyNames.add("auto-lock-files");
      }
      WorkspacePropertyListCmd.listProperties(workspaceSelectorList, null, propertyNames, client, config);
    } else if (cli.hasOption(ShowAttributesCmd.OPT_SNAPSHOT)) {
      if ((!cli.hasOption(OPT_NAME)) && (!cli.hasOption(OPT_DESCRIPTION)) && 
        (!cli.hasOption(OPT_OWNEDBY)) && (!cli.hasOption(OPT_ITERATION)) && (!cli.hasOption(OPT_RELEASE))) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.Common_MUST_SPECIFY_1_OF_4_ARGUMENTS, 
          new String[] { OPT_NAME.getName(), OPT_DESCRIPTION.getName(), 
          OPT_OWNEDBY.getName(), OPT_ITERATION.getName(), OPT_RELEASE.getName() }));
      }
      
      IScmCommandLineArgument ssSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowAttributesCmd.OPT_SNAPSHOT), config);
      SubcommandUtil.validateArgument(ssSelector, RepoUtil.ItemType.SNAPSHOT);
      
      IScmCommandLineArgument wsSelector = null;
      if (cli.hasOption(ShowAttributesCmd.OPT_SNAPSHOT_WORKSPACE)) {
        wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowAttributesCmd.OPT_SNAPSHOT_WORKSPACE), config);
        SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      }
      
      List<String> propertyNames = new ArrayList();
      if (cli.hasOption(OPT_NAME)) {
        propertyNames.add("name");
      }
      if (cli.hasOption(OPT_DESCRIPTION)) {
        propertyNames.add("description");
      }
      if (cli.hasOption(OPT_OWNEDBY)) {
        propertyNames.add("ownedby");
      }
      if (cli.hasOption(OPT_ITERATION)) {
        propertyNames.add("iteration");
      }
      if (cli.hasOption(OPT_RELEASE)) {
        propertyNames.add("release");
      }
      
      SnapshotPropertyListCmd.listProperties(ssSelector, wsSelector, propertyNames, true, client, config);
    } else if (cli.hasOption(ShowAttributesCmd.OPT_COMPONENT)) {
      if ((!cli.hasOption(OPT_NAME)) && (!cli.hasOption(OPT_OWNEDBY)) && 
        (!cli.hasOption(OPT_VISIBILITY))) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.Common_MUST_SPECIFY_1_OF_3_ARGUMENTS, 
          new String[] { OPT_NAME.getName(), OPT_OWNEDBY.getName(), 
          OPT_VISIBILITY.getName() }));
      }
      
      List<IScmCommandLineArgument> componentSelectorList = ScmCommandLineArgument.createList(cli.getOptionValues(ShowAttributesCmd.OPT_COMPONENT), config);
      SubcommandUtil.validateArgument(componentSelectorList, RepoUtil.ItemType.COMPONENT);
      
      List<String> propertyNames = new ArrayList();
      if (cli.hasOption(OPT_NAME)) {
        propertyNames.add("name");
      }
      if (cli.hasOption(OPT_DESCRIPTION)) {
        propertyNames.add("description");
      }
      if (cli.hasOption(OPT_OWNEDBY)) {
        propertyNames.add("ownedby");
      }
      if (cli.hasOption(OPT_VISIBILITY)) {
        propertyNames.add("visibility");
      }
      ComponentPropertyListCmd.listProperties(componentSelectorList, null, propertyNames, client, config);
    } else {
      if ((!cli.hasOption(OPT_NAME)) && (!cli.hasOption(OPT_DESCRIPTION))) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.Common_MUST_SPECIFY_1_OF_2_ARGUMENTS, 
          new String[] { OPT_NAME.getName(), OPT_DESCRIPTION.getName() }));
      }
      
      IScmCommandLineArgument blSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowAttributesCmd.OPT_BASELINE), config);
      SubcommandUtil.validateArgument(blSelector, RepoUtil.ItemType.BASELINE);
      
      IScmCommandLineArgument compSelector = null;
      if (cli.hasOption(ShowAttributesCmd.OPT_BASELINE_COMPONENT)) {
        compSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowAttributesCmd.OPT_BASELINE_COMPONENT), config);
        SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
      }
      
      List<String> propertyNames = new ArrayList();
      if (cli.hasOption(OPT_NAME)) {
        propertyNames.add("name");
      }
      if (cli.hasOption(OPT_DESCRIPTION)) {
        propertyNames.add("description");
      }
      
      BaselinePropertyListCmd.listProperties(blSelector, compSelector, propertyNames, true, client, config);
    }
  }
}
