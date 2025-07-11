import java.util.List;
import java.util.Scanner;

public class ShellPrincipal {
    public static void main(String[] args) {
        Scanner scan = new Scanner(System.in);
        GerenciadorLog gerenciadorLog = null;
        ExecutorComandos executor = null;

        try {
            gerenciadorLog = new GerenciadorLog();
            interpretadordecomandos interpretador = new interpretadordecomandos();
            executor = new ExecutorComandos(gerenciadorLog);

            boolean ehParaSair = false;
            System.out.println("Bem-vindo ao LaianaShell!");

            while (!ehParaSair) {
                System.out.print("laianashel>");
                String entradaUsuario = scan.nextLine();
                String linhaComando = entradaUsuario.trim();

                if (linhaComando.isEmpty()) {
                    continue;
                }

                if (linhaComando.equalsIgnoreCase("exit")) {
                    System.out.println("Saindo do shell. Até mais!");
                    ehParaSair = true;
                } else {
                    try {
                        // O interpretador agora retorna uma LISTA de ResultadoProcessamentoComando
                        List<ResultadoProcessamentoComando> comandosProcessados = interpretador.processarComando(linhaComando);

                        if (comandosProcessados != null && !comandosProcessados.isEmpty()) {
                            // Passa a LISTA de comandos para o executor
                            executor.executarComando(comandosProcessados);
                        } else {
                            System.err.println("Comando inválido ou não reconhecido.");
                        }

                    } catch (Exception e) {
                        System.err.println("Erro interno do shell: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro fatal ao iniciar o shell: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (scan != null) {
                scan.close();
            }
            if (gerenciadorLog != null) {
                gerenciadorLog.fecharLog();
            }
        }
    }
}
