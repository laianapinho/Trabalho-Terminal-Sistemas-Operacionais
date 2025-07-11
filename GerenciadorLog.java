import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter; 
import java.util.List; 

public class GerenciadorLog { 
    private BufferedWriter escritorLog; // Variável de instância para o escritor do arquivo de log
    private String nomeArquivoLog; // Variável de instância para nome do arquivo

    // Construtor da classe GerenciadorLog
    public GerenciadorLog(){
        LocalDateTime dataAtual = LocalDateTime.now(); // Obtém a data e hora atuais do sistema

        // Define um formatador para o nome do arquivo de log (ex: "20231027_103045")
        DateTimeFormatter formatadorNomeArquivo = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        // Constrói o nome do arquivo de log usando a data e hora formatadas
        nomeArquivoLog = "shell_log_" + dataAtual.format(formatadorNomeArquivo) + ".txt";

        // Tenta abrir o arquivo de log para escrita
        try {
            // Cria um FileWriter para o nome do arquivo, com 'true' para modo de anexar (append)
            // Envolve o FileWriter em um BufferedWriter para escrita mais eficiente
            escritorLog = new BufferedWriter(new FileWriter(nomeArquivoLog, true));
            // Escreve a mensagem de início de sessão no log, seguida por uma nova linha
            escreverNaLog("--- Sessão do Shell Iniciada: " + dataAtual.format(FORMATADOR_PADRAO_LOG()) + " ---");
            // Escreve o nome do arquivo de log no próprio log, seguida por uma nova linha
            escreverNaLog("Arquivo de log: " + nomeArquivoLog);
            // Garante que o buffer seja descarregado para o arquivo imediatamente
            escritorLog.flush();
        } catch (IOException e) {
            // Em caso de erro ao iniciar o log, imprime uma mensagem de erro no console
            System.err.println("Erro ao iniciar o arquivo de log: " + e.getMessage());
            escritorLog = null; // Define escritorLog como nulo para indicar que o log não pôde ser aberto
        }
    }

    // Método para registrar um comando completo no arquivo de log
    public void registrarComando(String comandoCompleto){
        // Verifica se o escritor de log foi inicializado com sucesso (não é nulo)
        if(escritorLog != null){
            try {
                LocalDateTime dataHoraRegistro = LocalDateTime.now(); // Obtém a data e hora atuais para o registro
                // Escreve usando [CMD] como categoria
                escreverNaLog(FORMATADOR_PADRAO_LOG().format(dataHoraRegistro) + " [CMD] " + comandoCompleto);
                escritorLog.flush(); // Força a escrita imediata do buffer para o arquivo
            } catch (IOException e) {
                // Em caso de erro ao registrar, imprime uma mensagem de erro no console
                System.err.println("Erro ao registrar comando no log: " + e.getMessage());
            }
        }
    }

    // Método para registrar uma chamada a programa externo no arquivo de log
    public void registrarChamadaPrograma(String programa, List<String> argumentos){
        if(escritorLog != null){
            try {
                LocalDateTime dataHoraRegistro = LocalDateTime.now(); // Obtém a data e hora atuais
                StringBuilder argsStr = new StringBuilder(); // Usa StringBuilder para construir a string de argumentos
                for (String arg : argumentos) {
                    argsStr.append(arg).append(" "); // Adiciona cada argumento seguido de um espaço
                }
                String argsFormatados = argsStr.toString().trim(); // Converte para String e remove espaços extras

                // Usar [CMD] para programas externos também, como se fossem comandos
                String comandoCompleto = programa + (argsFormatados.isEmpty() ? "" : " " + argsFormatados);
                escreverNaLog(FORMATADOR_PADRAO_LOG().format(dataHoraRegistro) + " [CMD] " + comandoCompleto);
                escritorLog.flush(); // Força a escrita imediata do buffer para o arquivo
            } catch (IOException e) {
                // Em caso de erro ao registrar, imprime uma mensagem de erro no console
                System.err.println("Erro ao registrar chamada de programa no log: " + e.getMessage());
            }
        }
    }

    // NOVO: Método para registrar códigos de saída de processos
    public void registrarCodigoSaida(int codigoSaida){
        if(escritorLog != null){
            try {
                LocalDateTime dataHoraRegistro = LocalDateTime.now();
                escreverNaLog(FORMATADOR_PADRAO_LOG().format(dataHoraRegistro) + " [LOG] Processo finalizado com código: " + codigoSaida);
                escritorLog.flush();
            } catch (IOException e) {
                System.err.println("Erro ao registrar código de saída no log: " + e.getMessage());
            }
        }
    }

    // NOVO: Método para registrar execução em background
    public void registrarProcessoBackground(String comando){
        if(escritorLog != null){
            try {
                LocalDateTime dataHoraRegistro = LocalDateTime.now();
                escreverNaLog(FORMATADOR_PADRAO_LOG().format(dataHoraRegistro) + " [LOG] Processo em background iniciado: " + comando);
                escritorLog.flush();
            } catch (IOException e) {
                System.err.println("Erro ao registrar processo background no log: " + e.getMessage());
            }
        }
    }

    // Método para registrar mudança de diretório
    public void registrarMudancaDiretorio(String diretorioAnterior, String novoDiretorio){
        if(escritorLog != null){
            try {
                LocalDateTime dataHoraRegistro = LocalDateTime.now();
                escreverNaLog(FORMATADOR_PADRAO_LOG().format(dataHoraRegistro) + " [LOG] Diretório alterado: " + diretorioAnterior + " → " + novoDiretorio);
                escritorLog.flush();
            } catch (IOException e) {
                System.err.println("Erro ao registrar mudança de diretório no log: " + e.getMessage());
            }
        }
    }

    // Método para registrar erros
    public void registrarErro(String mensagemErro){
        if(escritorLog != null){
            try {
                LocalDateTime dataHoraRegistro = LocalDateTime.now();
                escreverNaLog(FORMATADOR_PADRAO_LOG().format(dataHoraRegistro) + " [ERROR] " + mensagemErro);
                escritorLog.flush();
            } catch (IOException e) {
                System.err.println("Erro ao registrar erro no log: " + e.getMessage());
            }
        }
    }

    // Método para fechar o arquivo de log
    public void fecharLog(){
        // Verifica se o escritor de log está aberto (não é nulo)
        if(escritorLog != null){
            try {
                LocalDateTime dataHoraRegistro = LocalDateTime.now();
                // Registra saída como [LOG]
                escreverNaLog(FORMATADOR_PADRAO_LOG().format(dataHoraRegistro) + " [LOG] Saindo do shell.");
                escreverNaLog("--- Sessão do Shell Encerrada ---");
                escritorLog.flush(); // Garante que tudo no buffer seja gravado antes de fechar
                escritorLog.close(); // Fecha o arquivo de log, liberando os recursos
            } catch (IOException e) {
                // Em caso de erro ao fechar, imprime uma mensagem de erro no console
                System.err.println("Erro ao fechar o arquivo de log: " + e.getMessage());
            }
        }
    }

    // --- Funções Auxiliares Internas ---

    // Método auxiliar para escrever uma linha de texto no log e adicionar uma quebra de linha
    private void escreverNaLog(String texto) throws IOException {
        escritorLog.write(texto); // Escreve o texto no buffer
        escritorLog.newLine();    // Adiciona uma nova linha (compatível com diferentes SOs)
    }

    // Método auxiliar para obter um formatador de data/hora padrão para os registros de log
    private DateTimeFormatter FORMATADOR_PADRAO_LOG(){
        // Define o padrão de formatação (ex: "2023-10-27 10:30:45")
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }
}