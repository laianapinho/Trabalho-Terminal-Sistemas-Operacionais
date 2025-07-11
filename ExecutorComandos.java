import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExecutorComandos {

    private GerenciadorLog gerenciadorLog;

    public ExecutorComandos(GerenciadorLog log) {
        this.gerenciadorLog = log;
    }

    public void executarComando(List<ResultadoProcessamentoComando> listaDeComandos) {
        // 1. Logar o comando recebido para execução
        if (gerenciadorLog != null) {
            StringBuilder logCompleto = new StringBuilder();
            for (int i = 0; i < listaDeComandos.size(); i++) {
                ResultadoProcessamentoComando cmd = listaDeComandos.get(i);
                logCompleto.append(cmd.comandoPrincipal);
                if (cmd.argumentos != null && !cmd.argumentos.isEmpty()) {
                    logCompleto.append(" ").append(String.join(" ", cmd.argumentos));
                }
                if (cmd.arquivoEntradaRedirecionado != null) logCompleto.append(" < ").append(cmd.arquivoEntradaRedirecionado);
                if (cmd.arquivoSaidaRedirecionado != null) {
                    logCompleto.append(cmd.ehSaidaAppend ? " >> " : " > ").append(cmd.arquivoSaidaRedirecionado);
                }
                if (cmd.ehBackground) logCompleto.append(" &");
                if (cmd.temPipe && i < listaDeComandos.size() - 1) logCompleto.append(" | ");
            }
            gerenciadorLog.registrarComando(logCompleto.toString());
        }

        // 2. Lógica para cadeia de pipes
        if (listaDeComandos.size() > 1) {
            executarCadeiaDePipes(listaDeComandos);
            return;
        }

        // 3. Lógica para um ÚNICO comando
        ResultadoProcessamentoComando resultadoComando = listaDeComandos.get(0);

        // 4. Verificar se há pipe
        if (resultadoComando.temPipe) {
             System.err.println("Erro: Operador de pipe '|' encontrado em comando único. Sintaxe inválida.");
             if (gerenciadorLog != null) {
                 gerenciadorLog.registrarErro("Operador de pipe encontrado em comando único");
             }
             return;
        }

        // Comando "exit"
        if(resultadoComando.comandoPrincipal.equals("exit")){
            System.out.println("Sinal de saída recebido pelo Executor.");
            if (gerenciadorLog != null) {
                gerenciadorLog.fecharLog();
            }
        }
        // Comando "cd"
        else if(resultadoComando.comandoPrincipal.equals("cd")){
            if(resultadoComando.argumentos.isEmpty() || resultadoComando.argumentos.size() > 1){
                System.err.println("Uso: cd <caminho>");
                System.err.println("Altera o diretório de trabalho atual.");
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("cd: número incorreto de argumentos");
                }
            }
            else{
                String diretorioAnterior = obterDiretorioAtual(); // Salvar diretório atual antes da mudança
                String novoCaminho = resultadoComando.argumentos.get(0);

                if(novoCaminho.equals("..")){
                    String diretorioAtual = obterDiretorioAtual();
                    File arquivoDiretorioAtual = new File(diretorioAtual);
                    String diretorioPai = arquivoDiretorioAtual.getParent();

                    if (diretorioPai != null){
                        novoCaminho = diretorioPai;
                    }
                    else{
                        System.err.println("Já no diretório raiz.");
                        if (gerenciadorLog != null) {
                            gerenciadorLog.registrarErro("cd: já no diretório raiz");
                        }
                        return;
                    }
                }
                else if (novoCaminho.equals("/")){
                    novoCaminho = "/";
                }
                else if (novoCaminho.equals("~")) {
                    novoCaminho = obterDiretorioHome();
                }
                else if (novoCaminho.equals(".")) {
                    novoCaminho = obterDiretorioAtual(); // Fica no diretório atual
                }

                try {
                    mudarDiretorio(novoCaminho);
                    // NOVO: Registrar mudança de diretório no log
                    if (gerenciadorLog != null) {
                        gerenciadorLog.registrarMudancaDiretorio(diretorioAnterior, novoCaminho);
                    }
                } catch (Exception e) {
                    System.err.println("Erro ao mudar diretório: " + e.getMessage());
                    if (gerenciadorLog != null) {
                        gerenciadorLog.registrarErro("cd: " + e.getMessage());
                    }
                }
            }
        }
        // Comando "pwd"
        else if(resultadoComando.comandoPrincipal.equals("pwd")){
            if(resultadoComando.argumentos.size() > 0){
                System.err.println("Uso: pwd (não aceita argumentos)");
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("pwd: não aceita argumentos");
                }
            }
            else{
                mostrarDiretorioAtual();
            }
        }
        // Comando "cp"
        else if(resultadoComando.comandoPrincipal.equals("cp")){
            if(resultadoComando.argumentos.size() != 2){
                System.err.println("Uso: cp <origem> <destino>");
                System.err.println("Copia um arquivo de origem para o destino.");
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("cp: número incorreto de argumentos");
                }
            }
            else{
                String origem = resultadoComando.argumentos.get(0);
                String destino = resultadoComando.argumentos.get(1);
                try {
                    copiarArquivo(origem, destino);
                    System.out.println("Arquivo copiado com sucesso.");
                } catch (Exception e) {
                    System.err.println("Erro ao copiar arquivo: " + e.getMessage());
                    if (gerenciadorLog != null) {
                        gerenciadorLog.registrarErro("cp: " + e.getMessage());
                    }
                }
            }
        }
        // Comando "mv"
        else if(resultadoComando.comandoPrincipal.equals("mv")){
            if(resultadoComando.argumentos.size() != 2){
                System.err.println("Uso: mv <origem> <destino>");
                System.err.println("Renomeia ou move um arquivo/diretório.");
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("mv: número incorreto de argumentos");
                }
            }else{
                String origem = resultadoComando.argumentos.get(0);
                String destino = resultadoComando.argumentos.get(1);
                try {
                    moverArquivo(origem,destino);
                    System.out.println("Arquivo/diretório movido/renomeado com sucesso.");
                } catch (Exception e) {
                    System.err.println("Erro ao mover/renomear: " + e.getMessage());
                    if (gerenciadorLog != null) {
                        gerenciadorLog.registrarErro("mv: " + e.getMessage());
                    }
                }
            }
        }
        // Comando "rm"
        else if(resultadoComando.comandoPrincipal.equals("rm")){
            if(resultadoComando.argumentos.size() != 1){
                System.err.println("Uso: rm <arquivo/diretório>");
                System.err.println("Remove um arquivo ou diretório vazio.");
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("rm: número incorreto de argumentos");
                }
            }
            else{
                String alvo = resultadoComando.argumentos.get(0);
                try {
                    removerArquivo(alvo);
                    System.out.println("Arquivo/diretório removido com sucesso.");
                } catch (Exception e) {
                    System.err.println("Erro ao remover: " + e.getMessage());
                    if (gerenciadorLog != null) {
                        gerenciadorLog.registrarErro("rm: " + e.getMessage());
                    }
                }
            }
        }
        // Comando "mkdir"
        else if(resultadoComando.comandoPrincipal.equals("mkdir")){
            if(resultadoComando.argumentos.size() != 1){
                System.err.println("Uso: mkdir <caminho_do_diretório>");
                System.err.println("Cria um novo diretório.");
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("mkdir: número incorreto de argumentos");
                }
            }
            else{
                String novoDiretorio = resultadoComando.argumentos.get(0);
                try {
                    criarDiretorio(novoDiretorio);
                    System.out.println("Diretório '" + novoDiretorio + "' criado com sucesso.");
                } catch (Exception e) {
                    System.err.println("Erro ao criar diretório: "  + e.getMessage());
                    if (gerenciadorLog != null) {
                        gerenciadorLog.registrarErro("mkdir: " + e.getMessage());
                    }
                }
            }
        }
        // Comando "ls"
        else if (resultadoComando.comandoPrincipal.equals("ls")) {
            if (resultadoComando.argumentos.isEmpty()) {
                String caminhoListar = obterDiretorioAtual();
                try {
                    listarArquivos(caminhoListar);
                } catch (Exception e) {
                    System.err.println("Erro ao listar diretório: "  + e.getMessage());
                    if (gerenciadorLog != null) {
                        gerenciadorLog.registrarErro("ls: " + e.getMessage());
                    }
                }
            } else {
                executarComandoExterno(resultadoComando);
                return;
            }
        }
        // Comando "cat"
        else if(resultadoComando.comandoPrincipal.equals("cat")) {
            if (resultadoComando.argumentos.size() != 1) {
                System.err.println("Uso: cat <nome_do_arquivo>");
                System.err.println("Exibe o conteúdo de um arquivo de texto.");
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("cat: número incorreto de argumentos");
                }
            }else{
                String arquivoAlvo = resultadoComando.argumentos.get(0);
                try {
                    exibirConteudoArquivo(arquivoAlvo);
                } catch (Exception e) {
                    System.err.println("Erro ao exibir conteúdo: " + e.getMessage());
                    if (gerenciadorLog != null) {
                        gerenciadorLog.registrarErro("cat: " + e.getMessage());
                    }
                }
            }
        }
        // Comando "echo"
        else if (resultadoComando.comandoPrincipal.equals("echo")) {
            if (resultadoComando.arquivoSaidaRedirecionado != null) {
                File arquivoSaida = new File(resultadoComando.arquivoSaidaRedirecionado);
                try (FileOutputStream fos = new FileOutputStream(arquivoSaida, resultadoComando.ehSaidaAppend);
                     PrintStream ps = new PrintStream(fos)) {
                    imprimirTexto(resultadoComando.argumentos, ps);
                    System.out.println("Texto escrito em '" + resultadoComando.arquivoSaidaRedirecionado + "'");
                } catch (IOException e) {
                    System.err.println("Erro ao redirecionar saída para arquivo: " + e.getMessage());
                    if (gerenciadorLog != null) {
                        gerenciadorLog.registrarErro("echo: " + e.getMessage());
                    }
                }
            } else {
                imprimirTexto(resultadoComando.argumentos, System.out);
            }
        }
        // Comando "touch"
        else if (resultadoComando.comandoPrincipal.equals("touch")) {
            if (resultadoComando.argumentos.size() != 1)  {
                System.err.println("Uso: touch <nome_do_arquivo>");
                System.err.println("Cria um novo arquivo vazio ou atualiza o timestamp de um existente.");
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("touch: número incorreto de argumentos");
                }
            }
            else{
                String novoArquivo = resultadoComando.argumentos.get(0);
                try {
                    criarArquivoVazio(novoArquivo);
                } catch (Exception e) {
                    System.err.println("Erro ao criar/atualizar arquivo: " + e.getMessage());
                    if (gerenciadorLog != null) {
                        gerenciadorLog.registrarErro("touch: " + e.getMessage());
                    }
                }
            }
        }
        // Comando "help"
        else if (resultadoComando.comandoPrincipal.equals("help")) {
            if (resultadoComando.argumentos.size() > 1) {
                System.err.println("Uso: help [comando]");
                System.err.println("Mostra ajuda geral ou ajuda específica de um comando.");
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("help: muitos argumentos");
                }
            }
            else if (resultadoComando.argumentos.isEmpty()) {
                // Ajuda geral
                mostrarAjudaGeral();
            }
            else {
                // Ajuda específica de um comando
                String comando = resultadoComando.argumentos.get(0);
                mostrarAjudaComando(comando);
            }
        }
        // Bloco ELSE final: Execução de programas externos
        else {
            executarComandoExterno(resultadoComando);
        }
    }

    // Método para executar comandos externos com novo sistema de log
    private void executarComandoExterno(ResultadoProcessamentoComando resultadoComando) {
        try {
            List<String> listaComandoCompleta = new ArrayList<>();
            listaComandoCompleta.add(resultadoComando.comandoPrincipal);
            listaComandoCompleta.addAll(resultadoComando.argumentos);

            if (gerenciadorLog != null) {
                gerenciadorLog.registrarChamadaPrograma(resultadoComando.comandoPrincipal, resultadoComando.argumentos);
            }

            ProcessBuilder processoBuilder = new ProcessBuilder(listaComandoCompleta);
            processoBuilder.directory(new File(obterDiretorioAtual()));

            // Redirecionamento de Entrada (<)
            if (resultadoComando.arquivoEntradaRedirecionado != null) {
                File arquivoEntrada = new File(resultadoComando.arquivoEntradaRedirecionado);
                redirecionarEntrada(processoBuilder, arquivoEntrada);
            }

            // Redirecionamento de Saída (>, >>)
            if (resultadoComando.arquivoSaidaRedirecionado != null) {
                File arquivoSaida = new File(resultadoComando.arquivoSaidaRedirecionado);
                if (resultadoComando.ehSaidaAppend) {
                    redirecionarSaidaAppend(processoBuilder, arquivoSaida);
                } else {
                    redirecionarSaida(processoBuilder, arquivoSaida);
                }
            } else {
                processoBuilder.redirectOutput(Redirect.INHERIT);
            }

            processoBuilder.redirectError(Redirect.INHERIT);

            // Iniciar o processo
            Process processo = processoBuilder.start();

            // Lidar com execução em background (&)
            if (resultadoComando.ehBackground) {
                System.out.println("Comando '" + resultadoComando.comandoPrincipal + "' executado em background.");
                // NOVO: Registrar processo em background
                if (gerenciadorLog != null) {
                    String comandoCompleto = resultadoComando.comandoPrincipal + 
                        (resultadoComando.argumentos.isEmpty() ? "" : " " + String.join(" ", resultadoComando.argumentos));
                    gerenciadorLog.registrarProcessoBackground(comandoCompleto);
                }
            } else {
                int codigoSaida = processo.waitFor();
                System.out.println("Comando '" + resultadoComando.comandoPrincipal + "' finalizado com código " + codigoSaida);
                // NOVO: Registrar código de saída
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarCodigoSaida(codigoSaida);
                }
            }
            
        } catch (IOException e) {
            if (e.getMessage().contains("Cannot run program")) {
                System.err.println("bash: " + resultadoComando.comandoPrincipal + ": command not found");
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("Comando não encontrado: " + resultadoComando.comandoPrincipal);
                }
            } else {
                System.err.println("Erro ao executar comando: " + e.getMessage());
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("IO Error: " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            System.err.println("Comando interrompido");
            if (gerenciadorLog != null) {
                gerenciadorLog.registrarErro("Comando interrompido");
            }
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Erro inesperado durante a execução: " + e.getMessage());
            if (gerenciadorLog != null) {
                gerenciadorLog.registrarErro("Erro inesperado: " + e.getMessage());
            }
        }
    }

    private void executarLsExterno(List<String> argumentos) {
        try {
            List<String> comando = new ArrayList<>();
            comando.add("ls");
            comando.addAll(argumentos);

            if (gerenciadorLog != null) {
                gerenciadorLog.registrarChamadaPrograma("ls", argumentos);
            }

            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.directory(new File(obterDiretorioAtual()));

            Map<String, String> env = pb.environment();
            env.put("LC_ALL", "C");

            pb.redirectOutput(Redirect.INHERIT);
            pb.redirectError(Redirect.INHERIT);

            Process processo = pb.start();
            int codigoSaida = processo.waitFor();

            //Registrar código de saída do ls
            if (gerenciadorLog != null) {
                gerenciadorLog.registrarCodigoSaida(codigoSaida);
            }

            if (codigoSaida != 0) {
                System.err.println("ls terminou com código: " + codigoSaida);
            }

        } catch (Exception e) {
            System.err.println("Erro ao executar ls: " + e.getMessage());
            if (gerenciadorLog != null) {
                gerenciadorLog.registrarErro("ls: " + e.getMessage());
            }
        }
    }

    // --- Métodos auxiliares ---

    public void mostrarDiretorioAtual() {
        System.out.println(obterDiretorioAtual());
    }

    public String obterDiretorioAtual (){
        return System.getProperty("user.dir");
    }

    public String obterDiretorioHome (){
        return System.getProperty("user.home");
    }

    public void mudarDiretorio(String caminho) throws Exception {
        File arquivoDestino = new File(caminho);

        if (!arquivoDestino.exists()) {
            throw new Exception("Diretório '" + caminho + "' não encontrado.");
        }
        if (!arquivoDestino.isDirectory()) {
            throw new Exception("'" + caminho + "' não é um diretório.");
        }
        System.setProperty("user.dir", arquivoDestino.getAbsolutePath());
    }

    public void criarDiretorio(String caminho) throws Exception {
        File novoDir = new File(caminho);

        if(novoDir.exists()){
            throw new Exception("Diretório '" + caminho + "' já existe.");
        }else{
            boolean criado = novoDir.mkdir();

            if (!criado) {
                throw new Exception("Falha ao criar diretório '" + caminho + "'. Verifique permissões ou caminho.");
            }
        }
    }

    public void copiarArquivo(String origem, String destino) throws Exception {
        File arquivoOrigem = new File(origem);
        File arquivoDestino = new File(destino);

        if (!arquivoOrigem.exists()) {
            throw new Exception("Arquivo de origem '" + origem + "' não encontrado.");
        }

        if (arquivoOrigem.isDirectory()) {
            throw new Exception("Origem '" + origem + "' é um diretório, não um arquivo.");
        }

        try (FileInputStream fis = new FileInputStream(arquivoOrigem);
             FileOutputStream fos = new FileOutputStream(arquivoDestino)) {

            System.out.println("Copiando '" + origem + "' para '" + destino + "' (manual)...");

            byte[] buffer = new byte[1024];
            int bytesLidos;

            while ((bytesLidos = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesLidos);
            }

        } catch (IOException e) {
            throw new Exception("Falha ao copiar arquivo: " + e.getMessage(), e);
        }
    }

    public void moverArquivo(String origem, String destino) throws Exception {
        File arquivoOrigem = new File(origem);
        File arquivoDestino = new File(destino);

        if (!arquivoOrigem.exists()){
            throw new Exception("Arquivo/diretório de origem '" + origem + "' não encontrado.");
        }

        try {
            System.out.println("Movendo '" + origem + "' para '" + destino + "'...");

            boolean movidoComSucesso = arquivoOrigem.renameTo(arquivoDestino);

            if(!movidoComSucesso) {
                throw new Exception("Falha ao mover/renomear '" + origem + "' para '" + destino + "'. Verifique se o destino já existe e as permissões.");
            }
        } catch (Exception e) {
            throw new Exception("Falha inesperada ao mover/renomear: " + e.getMessage(), e);
        }
    }

     public void removerArquivo(String arquivo) throws Exception {
        File arquivoAlvo = new File(arquivo);

        if(!arquivoAlvo.exists()){
            throw new Exception("Arquivo/diretório '" + arquivo + "' não encontrado.");
        }

        boolean removidoComSucesso = arquivoAlvo.delete();

        if (removidoComSucesso) {
            System.out.println("Removendo '" + arquivo + "'...");
        } else {
            throw new Exception("Falha ao remover '" + arquivo + "'. Pode ser um diretório não vazio ou permissão negada.");
        }
    }

    public void listarArquivos(String caminhoOpcional) throws Exception {
        File diretorio = new File(caminhoOpcional);
        if(!diretorio.exists()){
            throw new Exception("Diretório '" + caminhoOpcional + "' não encontrado.");
        }
        else if(!diretorio.isDirectory()){
            throw new Exception("'" + caminhoOpcional + "' não é um diretório.");
        }
        try {
            System.out.println("Conteúdo de '" + caminhoOpcional + "':");
            Files.list(Paths.get(caminhoOpcional)).forEach(path -> System.out.println(path.getFileName()));
        } catch (IOException e) {
            throw new Exception("Falha ao listar arquivos: "  + e.getMessage(), e);
        }
    }

    public void exibirConteudoArquivo(String arquivo) throws Exception {
        File arquivoLido = new File(arquivo);

        if(!arquivoLido.exists()){
            throw new Exception("Arquivo '" + arquivo + "' não encontrado.");
        }
        else if(arquivoLido.isDirectory()){
            throw new Exception("'" + arquivo + "' é um diretório, não um arquivo.");
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivoLido))) {
            System.out.println("Conteúdo de '" + arquivo + "':");
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            throw new Exception("Falha ao ler arquivo: "  + e.getMessage(), e);
        }
    }

    public void imprimirTexto(List<String> argumentos, PrintStream streamSaida){
        if (argumentos == null || argumentos.isEmpty()) {
            streamSaida.println();
            return;
        }
        
        StringBuilder textoFinal = new StringBuilder();
        
        for (int i = 0; i < argumentos.size(); i++) {
            String arg = argumentos.get(i);
            
            if (arg.startsWith("\"") && arg.endsWith("\"") && arg.length() > 1) {
                arg = arg.substring(1, arg.length() - 1);
            }
            
            textoFinal.append(arg);
            
            if (i < argumentos.size() - 1) {
                textoFinal.append(" ");
            }
        }
        
        streamSaida.println(textoFinal.toString());
    }

    public void criarArquivoVazio(String arquivo)  throws Exception {
        File novoArquivo = new File(arquivo);

        if(novoArquivo.exists()){
            if (novoArquivo.isFile()) {
                boolean timestampAtualizado = novoArquivo.setLastModified(System.currentTimeMillis());
                if (timestampAtualizado) {
                    System.out.println("Arquivo '" + arquivo + "' já existe, timestamp atualizado.");
                } else {
                    System.err.println("Arquivo '" + arquivo + "' já existe, mas falha ao atualizar timestamp.");
                }
            } else if (novoArquivo.isDirectory()) {
                throw new Exception("'" + arquivo + "' é um diretório. Não é possível tocar um diretório.");
            } else {
                throw new Exception("Caminho '" + arquivo + "' existe, mas não é um arquivo regular.");
            }
        } else {
            try {
                boolean criado = novoArquivo.createNewFile();
                if (criado) {
                    System.out.println("Arquivo '" + arquivo + "' criado com sucesso.");
                } else {
                    throw new Exception("Falha ao criar arquivo '" + arquivo + "'. Pode ser um problema de permissão ou caminho inválido.");
                }
            } catch (IOException e) {
                throw new Exception("Falha ao criar arquivo '" + arquivo + "': " + e.getMessage(), e);
            }
        }
    }

    public void redirecionarEntrada(ProcessBuilder processoBuilder, File arquivo){
        processoBuilder.redirectInput(ProcessBuilder.Redirect.from(arquivo));
    }

    public void redirecionarSaida(ProcessBuilder processoBuilder, File arquivo){
        processoBuilder.redirectOutput(ProcessBuilder.Redirect.to(arquivo));
    }

     public void redirecionarSaidaAppend(ProcessBuilder processoBuilder, File arquivo){
        processoBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(arquivo));
    }

    // Método para exibir ajuda geral do shell
    private void mostrarAjudaGeral() {
        System.out.println("=== LAINASHELL - SISTEMA DE AJUDA ===");
        System.out.println();
        System.out.println("Comandos disponíveis:");
        System.out.println();
        System.out.println("NAVEGAÇÃO E SISTEMA:");
        System.out.println("  cd <caminho>     - Muda para o diretório especificado");
        System.out.println("  pwd              - Mostra o diretório atual");
        System.out.println("  ls [opções]      - Lista arquivos e diretórios");
        System.out.println("  exit             - Sai do shell");
        System.out.println();
        System.out.println("MANIPULAÇÃO DE ARQUIVOS:");
        System.out.println("  touch <arquivo>  - Cria arquivo vazio ou atualiza timestamp");
        System.out.println("  cat <arquivo>    - Exibe conteúdo de um arquivo");
        System.out.println("  cp <orig> <dest> - Copia arquivos");
        System.out.println("  mv <orig> <dest> - Move/renomeia arquivos ou diretórios");
        System.out.println("  rm <arquivo>     - Remove arquivos ou diretórios vazios");
        System.out.println("  mkdir <dir>      - Cria um novo diretório");
        System.out.println();
        System.out.println("TEXTO E SAÍDA:");
        System.out.println("  echo <texto>     - Imprime texto na tela");
        System.out.println();
        System.out.println("FUNCIONALIDADES AVANÇADAS:");
        System.out.println("  comando &        - Executa comando em background");
        System.out.println("  cmd1 | cmd2      - Conecta saída de cmd1 à entrada de cmd2 (pipe)");
        System.out.println("  cmd > arquivo    - Redireciona saída para arquivo");
        System.out.println("  cmd >> arquivo   - Adiciona saída ao final do arquivo");
        System.out.println("  cmd < arquivo    - Usa arquivo como entrada do comando");
        System.out.println();
        System.out.println("AJUDA:");
        System.out.println("  help             - Mostra esta ajuda");
        System.out.println("  help <comando>   - Mostra ajuda específica de um comando");
        System.out.println();
        System.out.println("CAMINHOS ESPECIAIS:");
        System.out.println("  ~                - Diretório home do usuário");
        System.out.println("  .                - Diretório atual");
        System.out.println("  ..               - Diretório pai");
        System.out.println("  ./arquivo        - Arquivo no diretório atual");
        System.out.println();
        System.out.println("Exemplos:");
        System.out.println("  ls -la | grep .txt");
        System.out.println("  echo \"Hello World\" > arquivo.txt");
        System.out.println("  cat arquivo.txt | wc -l");
        System.out.println("  sleep 10 &");
        System.out.println();
        System.out.println("Para ajuda específica, digite: help <comando>");
        System.out.println("====================================");
    }

    // Método para exibir ajuda específica de um comando
    private void mostrarAjudaComando(String comando) {
        System.out.println("=== AJUDA: " + comando.toUpperCase() + " ===");
        System.out.println();
        
        switch (comando.toLowerCase()) {
            case "cd":
                System.out.println("COMANDO: cd (Change Directory)");
                System.out.println("USO: cd <caminho>");
                System.out.println();
                System.out.println("DESCRIÇÃO:");
                System.out.println("  Muda o diretório de trabalho atual para o caminho especificado.");
                System.out.println();
                System.out.println("ARGUMENTOS:");
                System.out.println("  <caminho>    - Diretório de destino");
                System.out.println();
                System.out.println("CAMINHOS ESPECIAIS:");
                System.out.println("  cd ~         - Vai para o diretório home");
                System.out.println("  cd ..        - Volta para o diretório pai");
                System.out.println("  cd .         - Permanece no diretório atual");
                System.out.println("  cd /         - Vai para a raiz do sistema");
                System.out.println();
                System.out.println("EXEMPLOS:");
                System.out.println("  cd /home/usuario/documentos");
                System.out.println("  cd ../projetos");
                System.out.println("  cd ~");
                break;
                
            case "pwd":
                System.out.println("COMANDO: pwd (Print Working Directory)");
                System.out.println("USO: pwd");
                System.out.println();
                System.out.println("DESCRIÇÃO:");
                System.out.println("  Mostra o caminho completo do diretório atual.");
                System.out.println("  Este comando não aceita argumentos.");
                System.out.println();
                System.out.println("EXEMPLO:");
                System.out.println("  pwd");
                System.out.println("  # Saída: /home/usuario/documentos");
                break;
                
            case "ls":
                System.out.println("COMANDO: ls (List)");
                System.out.println("USO: ls [opções] [caminho]");
                System.out.println();
                System.out.println("DESCRIÇÃO:");
                System.out.println("  Lista arquivos e diretórios.");
                System.out.println("  Sem argumentos, lista o diretório atual.");
                System.out.println("  Com argumentos, executa o comando ls do sistema.");
                System.out.println();
                System.out.println("OPÇÕES COMUNS (comandos externos):");
                System.out.println("  -l           - Lista detalhada");
                System.out.println("  -a           - Mostra arquivos ocultos");
                System.out.println("  -la          - Lista detalhada com arquivos ocultos");
                System.out.println();
                System.out.println("EXEMPLOS:");
                System.out.println("  ls");
                System.out.println("  ls -la");
                System.out.println("  ls /home/usuario");
                break;
                
            case "touch":
                System.out.println("COMANDO: touch");
                System.out.println("USO: touch <arquivo>");
                System.out.println();
                System.out.println("DESCRIÇÃO:");
                System.out.println("  Cria um arquivo vazio se não existir, ou atualiza o");
                System.out.println("  timestamp de modificação se o arquivo já existir.");
                System.out.println();
                System.out.println("ARGUMENTOS:");
                System.out.println("  <arquivo>    - Nome do arquivo a ser criado/atualizado");
                System.out.println();
                System.out.println("EXEMPLOS:");
                System.out.println("  touch documento.txt");
                System.out.println("  touch /tmp/arquivo_temporario");
                break;
                
            case "cat":
                System.out.println("COMANDO: cat (Concatenate)");
                System.out.println("USO: cat <arquivo>");
                System.out.println();
                System.out.println("DESCRIÇÃO:");
                System.out.println("  Exibe o conteúdo de um arquivo de texto na tela.");
                System.out.println();
                System.out.println("ARGUMENTOS:");
                System.out.println("  <arquivo>    - Arquivo a ser exibido");
                System.out.println();
                System.out.println("EXEMPLOS:");
                System.out.println("  cat documento.txt");
                System.out.println("  cat /etc/passwd");
                System.out.println("  cat arquivo.txt | grep palavra");
                break;
                
            case "echo":
                System.out.println("COMANDO: echo");
                System.out.println("USO: echo <texto>");
                System.out.println();
                System.out.println("DESCRIÇÃO:");
                System.out.println("  Imprime texto na tela ou redireciona para arquivo.");
                System.out.println();
                System.out.println("ARGUMENTOS:");
                System.out.println("  <texto>      - Texto a ser impresso");
                System.out.println();
                System.out.println("EXEMPLOS:");
                System.out.println("  echo \"Hello World\"");
                System.out.println("  echo Texto sem aspas");
                System.out.println("  echo \"Conteúdo\" > arquivo.txt");
                System.out.println("  echo \"Mais texto\" >> arquivo.txt");
                break;
                
            case "cp":
                System.out.println("COMANDO: cp (Copy)");
                System.out.println("USO: cp <origem> <destino>");
                System.out.println();
                System.out.println("DESCRIÇÃO:");
                System.out.println("  Copia um arquivo de um local para outro.");
                System.out.println();
                System.out.println("ARGUMENTOS:");
                System.out.println("  <origem>     - Arquivo a ser copiado");
                System.out.println("  <destino>    - Local de destino da cópia");
                System.out.println();
                System.out.println("EXEMPLOS:");
                System.out.println("  cp arquivo.txt backup.txt");
                System.out.println("  cp documento.pdf /home/usuario/documentos/");
                break;
                
            case "mv":
                System.out.println("COMANDO: mv (Move)");
                System.out.println("USO: mv <origem> <destino>");
                System.out.println();
                System.out.println("DESCRIÇÃO:");
                System.out.println("  Move ou renomeia arquivos e diretórios.");
                System.out.println();
                System.out.println("ARGUMENTOS:");
                System.out.println("  <origem>     - Arquivo/diretório a ser movido");
                System.out.println("  <destino>    - Novo local ou nome");
                System.out.println();
                System.out.println("EXEMPLOS:");
                System.out.println("  mv arquivo.txt novo_nome.txt");
                System.out.println("  mv documento.pdf /home/usuario/documentos/");
                break;
                
            case "rm":
                System.out.println("COMANDO: rm (Remove)");
                System.out.println("USO: rm <arquivo/diretório>");
                System.out.println();
                System.out.println("DESCRIÇÃO:");
                System.out.println("  Remove arquivos ou diretórios vazios.");
                System.out.println("  ATENÇÃO: Esta operação é irreversível!");
                System.out.println();
                System.out.println("ARGUMENTOS:");
                System.out.println("  <arquivo>    - Arquivo ou diretório a ser removido");
                System.out.println();
                System.out.println("EXEMPLOS:");
                System.out.println("  rm arquivo.txt");
                System.out.println("  rm /tmp/arquivo_temporario");
                break;
                
            case "mkdir":
                System.out.println("COMANDO: mkdir (Make Directory)");
                System.out.println("USO: mkdir <diretório>");
                System.out.println();
                System.out.println("DESCRIÇÃO:");
                System.out.println("  Cria um novo diretório.");
                System.out.println();
                System.out.println("ARGUMENTOS:");
                System.out.println("  <diretório>  - Nome do diretório a ser criado");
                System.out.println();
                System.out.println("EXEMPLOS:");
                System.out.println("  mkdir projetos");
                System.out.println("  mkdir /home/usuario/nova_pasta");
                break;
                
            case "exit":
                System.out.println("COMANDO: exit");
                System.out.println("USO: exit");
                System.out.println();
                System.out.println("DESCRIÇÃO:");
                System.out.println("  Sai do LainaShell e retorna ao shell do sistema.");
                System.out.println("  Este comando não aceita argumentos.");
                System.out.println();
                System.out.println("EXEMPLO:");
                System.out.println("  exit");
                break;
                
            case "help":
                System.out.println("COMANDO: help");
                System.out.println("USO: help [comando]");
                System.out.println();
                System.out.println("DESCRIÇÃO:");
                System.out.println("  Mostra ajuda geral ou ajuda específica de um comando.");
                System.out.println();
                System.out.println("ARGUMENTOS:");
                System.out.println("  [comando]    - Comando específico para obter ajuda (opcional)");
                System.out.println();
                System.out.println("EXEMPLOS:");
                System.out.println("  help         - Mostra ajuda geral");
                System.out.println("  help cd      - Mostra ajuda do comando cd");
                System.out.println("  help ls      - Mostra ajuda do comando ls");
                break;
                
            default:
                System.out.println("Comando '" + comando + "' não reconhecido.");
                System.out.println();
                System.out.println("Comandos disponíveis:");
                System.out.println("  cd, pwd, ls, touch, cat, echo, cp, mv, rm, mkdir, exit, help");
                System.out.println();
                System.out.println("Digite 'help' para ver a lista completa com descrições.");
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("help: comando desconhecido - " + comando);
                }
                break;
        }
        System.out.println("=========================");
    }

    public void executarCadeiaDePipes(List<ResultadoProcessamentoComando> listaDeComandos){
        Process processoAnterior = null;
        java.io.InputStream streamSaidaProcessoAnterior = null;
        
        for (int i = 0; i < listaDeComandos.size(); i++){
            ResultadoProcessamentoComando comandoAtual = listaDeComandos.get(i);
            List<String> listaArgsProcesso  = new ArrayList<>();
            listaArgsProcesso.add(comandoAtual.comandoPrincipal);
            listaArgsProcesso.addAll(comandoAtual.argumentos);

            ProcessBuilder processoBuilder = new ProcessBuilder(listaArgsProcesso);
            try {
                processoBuilder.directory(new File(obterDiretorioAtual()));
            } catch (Exception e) {
                System.err.println("Erro ao definir diretório para pipe: " + e.getMessage());
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("Pipe: erro ao definir diretório - " + e.getMessage());
                }
                return;
            }

            // Conectar a ENTRADA do comando atual
            if(streamSaidaProcessoAnterior != null){
                processoBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
            }else if (comandoAtual.arquivoEntradaRedirecionado != null) {
                File arquivoEntrada = new File(comandoAtual.arquivoEntradaRedirecionado);
                processoBuilder.redirectInput(ProcessBuilder.Redirect.from(arquivoEntrada));
            }
            else{
                processoBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            }

            // Conectar a SAÍDA do comando atual
            if(i == listaDeComandos.size() - 1){
                if(comandoAtual.arquivoSaidaRedirecionado != null){
                    File arquivoSaida = new File (comandoAtual.arquivoSaidaRedirecionado);
                    if(comandoAtual.ehSaidaAppend){
                        processoBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(arquivoSaida));
                    } else {
                        processoBuilder.redirectOutput(ProcessBuilder.Redirect.to(arquivoSaida));
                    }
                } else {
                    processoBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }
            } else {
                processoBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
            }

            processoBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

            // Iniciar o processo
            try {
                Process processoAtual = processoBuilder.start();

                if (streamSaidaProcessoAnterior != null) {
                    final InputStream prevOut = streamSaidaProcessoAnterior;
                    final OutputStream currentIn = processoAtual.getOutputStream();
                    new Thread(() -> {
                        try {
                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = prevOut.read(buffer)) != -1) {
                                currentIn.write(buffer, 0, bytesRead);
                            }
                            prevOut.close();
                            currentIn.close();
                        } catch (IOException e) {
                            System.err.println("Erro na cópia de stream do pipe: " + e.getMessage());
                            if (gerenciadorLog != null) {
                                gerenciadorLog.registrarErro("Pipe: erro na cópia de stream - " + e.getMessage());
                            }
                        }
                    }).start();
                }

                if (i < listaDeComandos.size() - 1) {
                    streamSaidaProcessoAnterior = processoAtual.getInputStream();
                } else {
                    streamSaidaProcessoAnterior = null;
                }

                processoAnterior = processoAtual;

            } catch (IOException e) {
                System.err.println("Erro ao executar parte do pipe: " + e.getMessage());
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("Pipe: erro ao executar comando - " + e.getMessage());
                }
                return;
            }
        }

        // Esperar pelo ÚLTIMO processo do pipe terminar
        if (processoAnterior != null) {
            try {
                int codigoSaidaFinal = processoAnterior.waitFor();
                System.out.println("Cadeia de pipe finalizada com código: " + codigoSaidaFinal);
                // NOVO: Registrar código de saída da cadeia de pipes
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarCodigoSaida(codigoSaidaFinal);
                }
            } catch (InterruptedException e) {
                System.err.println("Cadeia de pipe interrompida: " + e.getMessage());
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("Pipe: cadeia interrompida - " + e.getMessage());
                }
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("Erro ao esperar por processo do pipe: " + e.getMessage());
                if (gerenciadorLog != null) {
                    gerenciadorLog.registrarErro("Pipe: erro ao esperar processo - " + e.getMessage());
                }
            }
        }
    }
}