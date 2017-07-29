package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.workspace.WorkspacePropertySetCmd;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.SimpleGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.osgi.util.NLS;









public class SetAttributesCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public static final NamedOptionDefinition OPT_NAME = new NamedOptionDefinition(null, "name", 1);
  public static final NamedOptionDefinition OPT_DESCRIPTION = new NamedOptionDefinition(null, "description", 1);
  public static final NamedOptionDefinition OPT_OWNEDBY = new NamedOptionDefinition(null, "ownedby", 1);
  public static final NamedOptionDefinition OPT_VISIBILITY = new NamedOptionDefinition(null, "visibility", 1);
  public static final NamedOptionDefinition OPT_PROCESS_AREA = new NamedOptionDefinition(null, "process-area", 1);
  public static final NamedOptionDefinition OPT_ACCESS_GROUP = new NamedOptionDefinition(null, "accessgroup", 1);
  public static final NamedOptionDefinition OPT_SCOPE_TO_TEAMAREA = new NamedOptionDefinition(null, "teamarea-scope", 0);
  public static final NamedOptionDefinition OPT_AUTO_FILE_LOCK_PATTTERN = new NamedOptionDefinition(null, "auto-lock-files", 1);
  public static final NamedOptionDefinition OPT_ITERATION = new NamedOptionDefinition(null, "iteration", 1);
  public static final NamedOptionDefinition OPT_RELEASE = new NamedOptionDefinition(null, "release", 1);
  public static final NamedOptionDefinition OPT_PROJECTAREA = new NamedOptionDefinition(null, "projectarea", 1);
  
  public SetAttributesCmd() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    options
    
      .addOption(new ContinuousGroup()
      .addOption(ShowAttributesCmd.OPT_WORKSPACE, Messages.SetAttributesCmdOptions_WORKSPACE_HELP, true)
      .addOption(new SimpleGroup(true)
      .addOption(OPT_NAME, Messages.SetAttributesCmdOptions_NAME_HELP, false)
      .addOption(OPT_DESCRIPTION, Messages.SetAttributesCmdOptions_DESCRIPTION_HELP, false)
      .addOption(OPT_OWNEDBY, Messages.SetAttributesCmdOptions_OWNEDBY_HELP, false)
      .addOption(OPT_VISIBILITY, Messages.SetAttributesCmdOptions_VISIBILITY_HELP, false))
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_PROCESS_AREA, NLS.bind(Messages.SetAttributesCmdOptions_PROCESS_AREA_HELP, 
      new String[] { OPT_VISIBILITY.getName(), "teamarea", "projectarea" }), false)
      .addOption(OPT_ACCESS_GROUP, NLS.bind(Messages.SetAttributesCmdOptions_ACCESS_GROUP_HELP, 
      new String[] { OPT_VISIBILITY.getName(), "accessgroup" }), false))
      .addOption(OPT_AUTO_FILE_LOCK_PATTTERN, Messages.SetAttributesCmdOptions_AUTO_FILE_LOCK_PATTERN_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(ShowAttributesCmd.OPT_SNAPSHOT, Messages.SetAttributesCmdOptions_SNAPSHOT_HELP, true)
      .addOption(ShowAttributesCmd.OPT_SNAPSHOT_WORKSPACE, Messages.SnapshotPropertiesCmdOptions_WORKSPACE_HELP, false)
      .addOption(OPT_PROJECTAREA, Messages.SnapshotPropertiesCmdOptions_PROJECTAREA_HELP, false)
      .addOption(new SimpleGroup(true)
      .addOption(OPT_NAME, Messages.SetAttributesCmdOptions_NAME_HELP, false)
      .addOption(OPT_DESCRIPTION, Messages.SetAttributesCmdOptions_DESCRIPTION_HELP, false)
      .addOption(OPT_ITERATION, Messages.SetAttributesCmdOptions_ITERATION_HELP, false)
      .addOption(OPT_OWNEDBY, Messages.SetAttributesCmdOptions_OWNEDBY_HELP, false)))
      .addOption(new ContinuousGroup()
      .addOption(ShowAttributesCmd.OPT_COMPONENT, Messages.SetAttributesCmdOptions_COMPONENT_HELP, true)
      .addOption(OPT_NAME, Messages.SetAttributesCmdOptions_NAME_HELP, false)
      .addOption(new SimpleGroup(true)
      .addOption(OPT_OWNEDBY, Messages.SetAttributesCmdOptions_OWNEDBY_HELP, false)
      .addOption(OPT_SCOPE_TO_TEAMAREA, NLS.bind(Messages.ComponentPropertiesCmdOptions_OPT_SCOPE_TO_TEAMAREA_HELP, OPT_OWNEDBY), false)
      .addOption(OPT_VISIBILITY, Messages.SetAttributesCmdOptions_VISIBILITY_HELP, false))
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_PROCESS_AREA, NLS.bind(Messages.SetAttributesCmdOptions_PROCESS_AREA_HELP, 
      new String[] { OPT_VISIBILITY.getName(), "teamarea", "projectarea" }), false)
      .addOption(OPT_ACCESS_GROUP, NLS.bind(Messages.SetAttributesCmdOptions_ACCESS_GROUP_HELP, 
      new String[] { OPT_VISIBILITY.getName(), "accessgroup" }), false)))
      .addOption(new ContinuousGroup()
      .addOption(ShowAttributesCmd.OPT_BASELINE, Messages.SetAttributesCmdOptions_BASELINE_HELP, true)
      .addOption(ShowAttributesCmd.OPT_BASELINE_COMPONENT, Messages.BaselinePropertiesCmdOptions_COMPONENT_HELP, false)
      .addOption(new SimpleGroup(true)
      .addOption(OPT_NAME, Messages.SetAttributesCmdOptions_NAME_HELP, false)
      .addOption(OPT_DESCRIPTION, Messages.SetAttributesCmdOptions_DESCRIPTION_HELP, false)));
    

    return options;
  }
  

  public void run()
    throws FileSystemException
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
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    if (cli.hasOption(ShowAttributesCmd.OPT_WORKSPACE)) {
      if ((!cli.hasOption(OPT_NAME)) && (!cli.hasOption(OPT_DESCRIPTION)) && 
        (!cli.hasOption(OPT_OWNEDBY)) && (!cli.hasOption(OPT_VISIBILITY)) && (!cli.hasOption(OPT_AUTO_FILE_LOCK_PATTTERN))) {
        throw StatusHelper.argSyntax(
        
          NLS.bind(Messages.Common_MUST_SPECIFY_1_OF_4_ARGUMENTS, new String[] { OPT_NAME.getName(), OPT_DESCRIPTION.getName(), OPT_OWNEDBY.getName(), OPT_VISIBILITY.getName() }) + " " + NLS.bind(Messages.Stream_MUST_SPECIFY_ARGUMENT, OPT_AUTO_FILE_LOCK_PATTTERN.getName()));
      }
      
      List<IScmCommandLineArgument> wsSelectorList = ScmCommandLineArgument.createList(cli.getOptionValues(ShowAttributesCmd.OPT_WORKSPACE), config);
      SubcommandUtil.validateArgument(wsSelectorList, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      
      Map<String, IScmCommandLineArgument> properties = new HashMap();
      if (cli.hasOption(OPT_NAME)) {
        properties.put("name", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_NAME), config));
      }
      if (cli.hasOption(OPT_DESCRIPTION)) {
        properties.put("description", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_DESCRIPTION), config));
      }
      if (cli.hasOption(OPT_OWNEDBY)) {
        properties.put("ownedby", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_OWNEDBY), config));
      }
      if (cli.hasOption(OPT_VISIBILITY)) {
        properties.put("visibility", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_VISIBILITY), config));
      }
      IScmCommandLineArgument visibilitySelector = null;
      if (cli.hasOption(OPT_PROCESS_AREA)) {
        visibilitySelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_PROCESS_AREA), config);
      } else if (cli.hasOption(OPT_ACCESS_GROUP)) {
        visibilitySelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_ACCESS_GROUP), config);
      }
      
      if (cli.hasOption(OPT_AUTO_FILE_LOCK_PATTTERN)) {
        properties.put("auto-lock-files", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_AUTO_FILE_LOCK_PATTTERN), config));
      }
      
      WorkspacePropertySetCmd.setProperties(wsSelectorList, properties, visibilitySelector, client, config);
    } else if (cli.hasOption(ShowAttributesCmd.OPT_SNAPSHOT)) {
      if ((!cli.hasOption(OPT_NAME)) && (!cli.hasOption(OPT_DESCRIPTION)) && (!cli.hasOption(OPT_OWNEDBY)) && 
        (!cli.hasOption(OPT_ITERATION)) && (!cli.hasOption(OPT_RELEASE))) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.Common_MUST_SPECIFY_1_OF_5_ARGUMENTS, 
          new String[] { OPT_NAME.getName(), OPT_DESCRIPTION.getName(), OPT_OWNEDBY.getName(), 
          OPT_ITERATION.getName(), OPT_RELEASE.getName() }));
      }
      
      IScmCommandLineArgument ssSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowAttributesCmd.OPT_SNAPSHOT), config);
      SubcommandUtil.validateArgument(ssSelector, RepoUtil.ItemType.SNAPSHOT);
      
      IScmCommandLineArgument wsSelector = null;
      if (cli.hasOption(ShowAttributesCmd.OPT_SNAPSHOT_WORKSPACE)) {
        wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShowAttributesCmd.OPT_SNAPSHOT_WORKSPACE), config);
        SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      }
      
      Map<String, IScmCommandLineArgument> properties = new HashMap();
      if (cli.hasOption(OPT_NAME)) {
        properties.put("name", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_NAME), config));
      }
      if (cli.hasOption(OPT_DESCRIPTION)) {
        properties.put("description", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_DESCRIPTION), config));
      }
      if (cli.hasOption(OPT_OWNEDBY)) {
        properties.put("ownedby", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_OWNEDBY), config));
      }
      if (cli.hasOption(OPT_ITERATION)) {
        properties.put("iteration", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_ITERATION), config));
      }
      
      IScmCommandLineArgument projectArea = null;
      if (cli.hasOption(OPT_PROJECTAREA)) {
        projectArea = ScmCommandLineArgument.create(cli.getOptionValue(OPT_PROJECTAREA), config);
        SubcommandUtil.validateArgument(projectArea, new RepoUtil.ItemType[] { RepoUtil.ItemType.PROJECTAREA, RepoUtil.ItemType.TEAMAREA });
      }
      
      SnapshotPropertySetCmd.setProperties(ssSelector, wsSelector, projectArea, properties, client, config);
    }
    else if (cli.hasOption(ShowAttributesCmd.OPT_COMPONENT)) {
      if ((!cli.hasOption(OPT_NAME)) && (!cli.hasOption(OPT_OWNEDBY)) && (!cli.hasOption(OPT_VISIBILITY))) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.Common_MUST_SPECIFY_1_OF_3_ARGUMENTS, 
          new String[] { OPT_NAME.getName(), OPT_OWNEDBY.getName(), OPT_VISIBILITY.getName() }));
      }
      
      List<IScmCommandLineArgument> componentSelectorList = ScmCommandLineArgument.createList(cli.getOptionValues(ShowAttributesCmd.OPT_COMPONENT), config);
      SubcommandUtil.validateArgument(componentSelectorList, RepoUtil.ItemType.COMPONENT);
      
      Map<String, IScmCommandLineArgument> properties = new HashMap();
      if (cli.hasOption(OPT_NAME)) {
        properties.put("name", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_NAME), config));
      }
      if (cli.hasOption(OPT_DESCRIPTION)) {
        properties.put("description", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_DESCRIPTION), config));
      }
      if (cli.hasOption(OPT_OWNEDBY)) {
        properties.put("ownedby", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_OWNEDBY), config));
      }
      if (cli.hasOption(OPT_VISIBILITY)) {
        properties.put("visibility", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_VISIBILITY), config));
      }
      if (cli.hasOption(OPT_PROCESS_AREA)) {
        properties.put("processarea", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_PROCESS_AREA), config));
      }
      if (cli.hasOption(OPT_ACCESS_GROUP)) {
        properties.put("accessgroup", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_ACCESS_GROUP), config));
      }
      
      ComponentPropertySetCmd.setProperties(componentSelectorList, properties, cli.hasOption(OPT_SCOPE_TO_TEAMAREA), client, config);
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
      
      Map<String, IScmCommandLineArgument> properties = new HashMap();
      if (cli.hasOption(OPT_NAME)) {
        properties.put("name", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_NAME), config));
      }
      if (cli.hasOption(OPT_DESCRIPTION)) {
        properties.put("description", 
          ScmCommandLineArgument.create(cli.getOptionValue(OPT_DESCRIPTION), config));
      }
      
      BaselinePropertySetCmd.setProperties(blSelector, compSelector, properties, client, config);
    }
  }
}
