import java.util.ArrayList;
import java.util.List;
public class ResultadoProcessamentoComando {
    public String comandoPrincipal;
    public List<String> argumentos;
    public boolean ehBackground;
    public boolean temPipe;
    public String arquivoEntradaRedirecionado;
    public String arquivoSaidaRedirecionado;
    public boolean ehSaidaAppend;

    public ResultadoProcessamentoComando() {
        this.argumentos = new ArrayList<>(); // Inicializa a lista para evitar NullPointerException
    }
}