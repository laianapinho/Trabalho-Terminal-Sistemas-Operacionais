import java.util.ArrayList;
import java.util.List;

public class interpretadordecomandos {
    public List<ResultadoProcessamentoComando> processarComando(String linhaComandoBruta){

        List<ResultadoProcessamentoComando> lista_resultados = new ArrayList<>();
        ResultadoProcessamentoComando resultadoAtual = new ResultadoProcessamentoComando();
        resultadoAtual.ehBackground = false;
        resultadoAtual.temPipe = false;
        resultadoAtual.arquivoEntradaRedirecionado = null;
        resultadoAtual.arquivoSaidaRedirecionado = null;
        resultadoAtual.ehSaidaAppend = false;

        // 2. Separar a linha de comando em tokens (palavras)
        List<String> tokens = dividirStringEmTokens(linhaComandoBruta);

        // 3. Verificar se há tokens (se a linha não era apenas espaços)
        if (tokens.isEmpty()){
            return lista_resultados; // Retorna uma lista vazia se não houver tokens
        }

        // 4. Iterar sobre TODOS os tokens para identificar comandos, argumentos, e operadores
        boolean ehPrimeiroTokenDoComando = true; // Flag para identificar o comando principal de cada sub-comando

        // 6. Iterar sobre os tokens para identificar argumentos e bônus
        // O loop agora começa do índice 0 para pegar o comando principal e avança 'i' manualmente
        for (int i = 0; i < tokens.size(); i++){
            String token = tokens.get(i); // Obtém o token atual

            if(ehPrimeiroTokenDoComando){
                resultadoAtual.comandoPrincipal = token; // O primeiro token é o comando principal
                ehPrimeiroTokenDoComando = false; // Próximos tokens não serão o comando principal
            }
            else if(token.equals("&")){
                resultadoAtual.ehBackground = true;
                // O '&' sempre se aplica ao ÚLTIMO comando da linha
                // Se houver um pipe antes do '&', ele se aplica ao comando após o último pipe.
            }
            else if(token.equals("|")){
                // Pipe encontrado!
                // 1. Marcar o comando atual como tendo um pipe (para o Executor saber que ele é parte de uma cadeia)
                resultadoAtual.temPipe = true;
                // 2. Adicionar o comando atual à lista de resultados
                lista_resultados.add(resultadoAtual);
                // 3. Criar um NOVO ResultadoProcessamentoComando para o PRÓXIMO comando no pipe
                resultadoAtual = new ResultadoProcessamentoComando();
                resultadoAtual.ehBackground = false; // Resetar flags para o novo comando
                resultadoAtual.temPipe = false;
                resultadoAtual.arquivoEntradaRedirecionado = null;
                resultadoAtual.arquivoSaidaRedirecionado = null;
                resultadoAtual.ehSaidaAppend = false;
                ehPrimeiroTokenDoComando = true; // O próximo token será o comando principal do novo sub-comando
            }
            else if(token.equals("<")){
                // Redirecionamento de entrada encontrado
                // Precisa verificar se existe um próximo token (o nome do arquivo)
                if (i + 1 < tokens.size()){ // Verifica se há um token após o '<'
                    resultadoAtual.arquivoEntradaRedirecionado = tokens.get(i + 1); // Obtém o nome do arquivo
                    i++; // AVANÇA O ÍNDICE para pular o nome do arquivo na próxima iteração
                } else {
                    System.err.println("Erro de sintaxe: redirecionamento de entrada '<' requer um nome de arquivo.");
                    // Em um shell real, isso poderia invalidar o comando ou lançar uma exceção.
                    // Por simplicidade, apenas imprime o erro e continua.
                }
            }
            else if(token.equals(">") || token.equals(">>")){
                if (token.equals(">>")){
                    resultadoAtual.ehSaidaAppend = true;
                }
                // Precisa verificar se existe um próximo token (o nome do arquivo de saída)
                if (i + 1 < tokens.size()){ // Verifica se há um token após o '>' ou '>>'
                    resultadoAtual.arquivoSaidaRedirecionado = tokens.get(i + 1); // Obtém o nome do arquivo
                    i++; // AVANÇA O ÍNDICE para pular o nome do arquivo na próxima iteração
                } else {
                    System.err.println("Erro de sintaxe: redirecionamento de saída '" + token + "' requer um nome de arquivo.");
                    // Em um shell real, isso poderia invalidar o comando ou lançar uma exceção.
                }
            }
            else { // É um argumento comum
                String argumentoNormal = token;
                // Expansão de expressões (./ e ~)
                if(argumentoNormal.startsWith("./")){
                    // Adiciona o diretório atual + o restante do caminho (sem o '.')
                    argumentoNormal = obterDiretorioAtual() + argumentoNormal.substring(1);
                } else if (argumentoNormal.startsWith("~")) {
                    // Adiciona o diretório home + o restante do caminho (sem o '~')
                    argumentoNormal = obterDiretorioHome() + argumentoNormal.substring(1);
                }
                resultadoAtual.argumentos.add(argumentoNormal); // CORRIGIDO: Adiciona o argumento à lista de argumentos do comando atual
            }
        }
        // Após o loop, adicionar o ÚLTIMO comando à lista de resultados
        // (Este é o comando que não foi seguido por um pipe)
        // Garante que o comando principal não é nulo ou vazio antes de adicionar
        if(resultadoAtual.comandoPrincipal != null && !resultadoAtual.comandoPrincipal.isEmpty()){
            lista_resultados.add(resultadoAtual);
        }

        return lista_resultados; // Retorna a lista de resultados (pode conter um ou mais comandos)

    } // Fim do método processarComando

    // NOVO MÉTODO CORRIGIDO - Trata aspas corretamente
    public List<String> dividirStringEmTokens(String texto) {
        List<String> tokens = new ArrayList<>();
        StringBuilder tokenAtual = new StringBuilder();
        boolean dentroDeAspas = false;
        char aspaTipo = '\0'; // Tipo de aspa (simples ou duplas)

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);

            if (!dentroDeAspas) {
                // Não estamos dentro de aspas
                if (c == '"' || c == '\'') {
                    // Começar uma string com aspas
                    dentroDeAspas = true;
                    aspaTipo = c;
                    // Não adicionar a aspa ao token
                } else if (Character.isWhitespace(c)) {
                    // Espaço encontrado - finalizar token atual se não estiver vazio
                    if (tokenAtual.length() > 0) {
                        tokens.add(tokenAtual.toString());
                        tokenAtual.setLength(0);
                    }
                } else if (isOperador(c, texto, i)) {
                    // Operador encontrado (>, <, |, &)
                    // Finalizar token atual se houver
                    if (tokenAtual.length() > 0) {
                        tokens.add(tokenAtual.toString());
                        tokenAtual.setLength(0);
                    }

                    // Tratar operadores especiais (>> )
                    if (c == '>' && i + 1 < texto.length() && texto.charAt(i + 1) == '>') {
                        tokens.add(">>");
                        i++; // Pular o próximo '>'
                    } else {
                        tokens.add(String.valueOf(c));
                    }
                } else {
                    // Caractere normal
                    tokenAtual.append(c);
                }
            } else {
                // Estamos dentro de aspas
                if (c == aspaTipo) {
                    // Fechar aspas
                    dentroDeAspas = false;
                    aspaTipo = '\0';
                    // Não adicionar a aspa ao token
                } else {
                    // Caractere dentro de aspas
                    tokenAtual.append(c);
                }
            }
        }

        // Adicionar último token se houver
        if (tokenAtual.length() > 0) {
            tokens.add(tokenAtual.toString());
        }

        // Verificar se aspas foram fechadas
        if (dentroDeAspas) {
            System.err.println("Erro: Aspas não fechadas no comando");
        }

        return tokens;
    }

    // Método auxiliar para verificar se um caractere é um operador
    private boolean isOperador(char c, String texto, int posicao) {
        return c == '>' || c == '<' || c == '|' || c == '&';
    }

    public String obterDiretorioAtual (){
        return System.getProperty("user.dir");
    }

    public String obterDiretorioHome (){
        return System.getProperty("user.home");
    }

    // Método de debug para testar o parsing (útil para depuração)
    public void debugTokens(String comando) {
        System.out.println("=== DEBUG TOKENS ===");
        System.out.println("Comando: " + comando);
        List<String> tokens = dividirStringEmTokens(comando);
        for (int i = 0; i < tokens.size(); i++) {
            System.out.println("Token " + i + ": '" + tokens.get(i) + "'");
        }
        System.out.println("===================");
    }
}
