package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.SimpleGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import org.eclipse.osgi.util.NLS;










public class ExtractFileCmdOptions
  implements IOptionSource
{
  public static final IPositionalOptionDefinition OPT_DISKPATH = new PositionalOptionDefinition("path-on-disk", 0, 1);
  

  public static final IPositionalOptionDefinition OPT_VER_ITEM = new PositionalOptionDefinition("item", 1, 1);
  

  public static final IPositionalOptionDefinition OPT_VER_STATE = new PositionalOptionDefinition("state", 0, 1);
  

  public static final NamedOptionDefinition OPT_OVERWRITE = new NamedOptionDefinition("o", "overwrite", 0);
  

  public static final NamedOptionDefinition OPT_BASELINE = new NamedOptionDefinition("b", "baseline", 0);
  
  public static final IPositionalOptionDefinition BASELINE = new PositionalOptionDefinition("baseline", 1, 1);
  

  public static final NamedOptionDefinition OPT_WORKSPACE = new NamedOptionDefinition("w", "workspace", 0);
  
  public static final IPositionalOptionDefinition WORKSPACE = new PositionalOptionDefinition("workspace", 1, 1);
  

  public static final NamedOptionDefinition OPT_SNAPSHOT = new NamedOptionDefinition("s", "snapshot", 0);
  
  public static final IPositionalOptionDefinition SNAPSHOT = new PositionalOptionDefinition("snapshot", 1, 1);
  

  public static final NamedOptionDefinition OPT_COMPONENT = new NamedOptionDefinition("c", "component", 1);
  
  public static final NamedOptionDefinition OPT_FILEPATH = new NamedOptionDefinition("f", "filepath", 1);
  
  public ExtractFileCmdOptions() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options
      .addOption(OPT_OVERWRITE, Messages.ChangesetExtractCmdOpts_OVERWRITE)
      .addOption(new MutuallyExclusiveGroup(true)
      .addOption(new SimpleGroup(false)
      .addOption(OPT_BASELINE, Messages.ExtractFileCmdOptions_BASELINE, true)
      .addOption(BASELINE, BASELINE.getName()))
      
      .addOption(new SimpleGroup(false)
      .addOption(OPT_WORKSPACE, Messages.ExtractFileCmdOptions_WORKSPACE, true)
      .addOption(WORKSPACE, WORKSPACE.getName()))
      
      .addOption(new SimpleGroup(false)
      .addOption(OPT_SNAPSHOT, Messages.ExtractFileCmdOptions_SNAPSHOT, true)
      .addOption(SNAPSHOT, SNAPSHOT.getName()))
      
      .addOption(OPT_VER_ITEM, Messages.ExtractFileCmdOptions_ITEM_ID))
      .addOption(OPT_COMPONENT, NLS.bind(Messages.ExtractFileCmdOptions_COMPONENT, 
      new Object[] {
      OPT_BASELINE.getShortOpt(), 
      OPT_WORKSPACE.getShortOpt(), 
      OPT_SNAPSHOT.getShortOpt() }), 
      false)
      .addOption(OPT_FILEPATH, NLS.bind(Messages.ExtractFileCmdOptions_FILEPATH, 
      new Object[] {
      OPT_BASELINE.getShortOpt(), 
      OPT_WORKSPACE.getShortOpt(), 
      OPT_SNAPSHOT.getShortOpt() }), 
      false)
      .addOption(OPT_VER_STATE, Messages.ExtractFileCmdOptions_STATE_ID)
      .addOption(OPT_DISKPATH, Messages.ChangesetExtractCmdOpts_PATH_ON_DISK);
    return options;
  }
}
