/* Generated By:JavaCC: Do not edit this line. ScriptParserDefaultVisitor.java Version 6.1_2 */
package com.serenegiant.lang.script;

public class ScriptParserDefaultVisitor implements ScriptParserVisitor{
  public Object defaultVisit(SimpleNode node, Object data){
    node.childrenAccept(this, data);
    return data;
  }
  public Object visit(SimpleNode node, Object data){
    return defaultVisit(node, data);
  }
}
/* JavaCC - OriginalChecksum=417f4b2f2f91eeaa7ff0ca733e7cfe21 (do not edit this line) */
