package com.ibm.team.filesystem.cli.client.internal.flowcommands.conflicthandlers;

import java.io.IOException;
import org.eclipse.compare.rangedifferencer.RangeDifference;


















public class GapInPlaceConflictHandler
  extends InPlaceConflictHandler
{
  public static final String MARKER_BEFORE = "======= before";
  public static final String MARKER_AFTER = "======= after";
  public static final String MARKER_END = ">>>>>>>";
  
  public GapInPlaceConflictHandler() {}
  
  protected String getMarkerProposed()
  {
    return "======= after";
  }
  
  protected String getMarkerEnd() {
    return ">>>>>>>";
  }
  
  protected void writeAncestor(InPlaceConflictHandler.Writer writer, InPlaceConflictHandler.LineRangeComparator ancestor, RangeDifference diff) throws IOException {
    writer.write("======= before");
    
    writer.write(ancestor, diff.ancestorStart(), diff.ancestorEnd());
  }
}
