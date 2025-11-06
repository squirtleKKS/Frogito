/**
 * Подпакет {@code lang.lexer} содержит реализацию лексического анализатора языка <b>Frogito</b>.
 * <p>
 * Лексер выполняет разбиение исходного кода программы на токены
 * и является первым этапом компиляции.
 * </p>
 *
 * <h2>Состав пакета:</h2>
 * <ul>
 *   <li>{@link lang.lexer.Token} — интерфейс токена с его типом, текстом и позицией;</li>
 *   <li>{@link lang.lexer.BaseToken} — базовая реализация интерфейса {@code Token};</li>
 *   <li>{@link lang.lexer.TokenType} — перечисление всех типов токенов языка Frogito;</li>
 *   <li>{@link lang.lexer.TokenPattern} — набор регулярных выражений, описывающих шаблоны токенов;</li>
 *   <li>{@link lang.lexer.Lexer} — основной класс лексера, выполняющий анализ исходного текста;</li>
 *   <li>{@link lang.lexer.LexingException} — исключение, выбрасываемое при ошибке токенизации.</li>
 * </ul>
 *
 * <p>
 * Приоритет шаблонов определяется порядком в списке {@link lang.lexer.TokenPattern#ALL}.
 * Ключевые слова и булевы литералы описаны при помощи метода {@code word(...)} и
 * распознаются только на границе слова (например, {@code trueFalse} лексируется как {@code IDENT}).
 * </p>
 *
 * <h2>Пример использования:</h2>
 * <pre>{@code
 * Lexer lexer = new Lexer("var int x = 10;");
 * for (Token token : lexer.tokenize()) {
 *     System.out.println(token);
 * }
 * }</pre>
 *
 * <p>
 * Реализация выполняется вручную, без использования ANTLR или JFlex,
 * в учебных целях для демонстрации базовых принципов построения компилятора.
 * </p>
 *
 * @author Frogito Compiler Team
 * @version 1.0
 * @since 1.0
 */
package lang.lexer;
