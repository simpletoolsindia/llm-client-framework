/**
 * Tool registration annotations and registry support.
 *
 * <p>The framework supports both lambda tools and annotation-driven tools.
 * Annotation-driven tools are convenient for regular Java services because the
 * method signature becomes the tool schema exposed to the model.</p>
 *
 * <pre>{@code
 * class TravelTools {
 *     @LLMTool(name = "city_tip", description = "Return a travel tip for a city")
 *     public String cityTip(
 *         @ToolParam(name = "city", description = "City name") String city,
 *         @ToolParam(name = "season", description = "Travel season", required = false) String season
 *     ) {
 *         return "Visit " + city + " early in the morning.";
 *     }
 * }
 * }</pre>
 */
package in.simpletools.llm.framework.tool;
