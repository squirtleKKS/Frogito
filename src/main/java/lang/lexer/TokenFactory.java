package lang.lexer;

/**
 * Фабрика токенов.
 * <p>
 * Используется лексером для создания экземпляров токенов без зависимости
 * от конкретной реализации ({@link BaseToken}).
 * </p>
 *
 * @see Token
 * @see BaseToken
 * @since 1.0
 */
public interface TokenFactory {

    /**
     * Создаёт токен указанного типа.
     *
     * @param type тип токена ({@link TokenType})
     * @param lexeme исходный текст токена
     * @param line номер строки (нумерация с 1)
     * @param column номер символа в строке (нумерация с 1)
     * @return новый экземпляр {@link Token}
     */
    Token create(TokenType type, String lexeme, int line, int column);

    /**
     * Возвращает стандартную фабрику токенов,
     * создающую объекты класса {@link BaseToken}.
     *
     * @return фабрика токенов по умолчанию
     */
    static TokenFactory defaultFactory() {
        return BaseToken::new;
    }
}
