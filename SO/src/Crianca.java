import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Classe Crianca: representa uma crianca (thread) que brinca com bolas.
 * A propria thread tambem e dona da animacao visual da crianca.
 */
public class Crianca implements Runnable {
    private static final int QUADRO_MS = 33;
    private static final double VELOCIDADE = 4.0;
    private static final double DISTANCIA_MINIMA = 82.0;
    private static final double DISTANCIA_ACAO_CESTO = 58.0;
    private static final int CPU_ITERACOES = 70_000;

    private final int id;
    private volatile boolean possuiBola;
    private final long tempoDebrincadeira;
    private final long tempoDescanso;
    private final Cesto cesto;
    private final Random random;

    private volatile EstadoCrianca estado;
    private volatile int contagemBrincadeiras;
    private volatile double x;
    private volatile double y;
    private volatile boolean olhandoParaDireita = true;
    private volatile double passoAnimacao;
    private volatile double acumuladorCpu;
    private volatile List<Crianca> criancasNoParque = Collections.emptyList();

    private volatile int larguraCena = 900;
    private volatile int alturaCena = 430;
    private volatile int cestoX = 780;
    private volatile int cestoY = 230;

    private double alvoBrincadeiraX;
    private double alvoBrincadeiraY;
    private double alvoDescansoX;
    private double alvoDescansoY;

    private EstadoListener estadoListener;

    public enum EstadoCrianca {
        BRINCANDO,
        ESPERANDO_BOLA,
        ESPERANDO_ESPACO,
        DESCANSANDO
    }

    @FunctionalInterface
    public interface EstadoListener {
        void estadoMudou(Crianca crianca);
    }

    public Crianca(int id, boolean possuiBola, long tempoDebrincadeira,
                   long tempoDescanso, Cesto cesto) {
        this.id = id;
        this.possuiBola = possuiBola;
        this.tempoDebrincadeira = tempoDebrincadeira;
        this.tempoDescanso = tempoDescanso;
        this.cesto = cesto;
        this.estado = possuiBola ? EstadoCrianca.BRINCANDO : EstadoCrianca.ESPERANDO_BOLA;
        this.contagemBrincadeiras = 0;
        this.random = new Random(id * 7919L + System.nanoTime());
        this.x = 60 + (id % 5) * 75;
        this.y = 90 + (id % 4) * 70;
        escolherNovoAlvoBrincadeira();
        escolherNovoAlvoDescanso();
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (!possuiBola) {
                    mudarEstado(EstadoCrianca.ESPERANDO_BOLA);
                    while (!estaPertoDoCesto() || !cesto.tentarPegarBola()) {
                        atualizarMovimento(EstadoCrianca.ESPERANDO_BOLA);
                    }
                    possuiBola = true;
                }

                mudarEstado(EstadoCrianca.BRINCANDO);
                executarPorTempo(tempoDebrincadeira, EstadoCrianca.BRINCANDO);
                contagemBrincadeiras++;

                mudarEstado(EstadoCrianca.ESPERANDO_ESPACO);
                while (!estaPertoDoPontoDeDevolucao() || !cesto.tentarDevolverBola()) {
                    atualizarMovimento(EstadoCrianca.ESPERANDO_ESPACO);
                }
                possuiBola = false;

                mudarEstado(EstadoCrianca.DESCANSANDO);
                executarPorTempo(tempoDescanso, EstadoCrianca.DESCANSANDO);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Crianca " + id + " foi interrompida");
        }
    }

    private void executarPorTempo(long duracaoMillis, EstadoCrianca estadoAtual) throws InterruptedException {
        long fim = System.currentTimeMillis() + duracaoMillis;
        long proximoQuadro = System.currentTimeMillis();
        double valor = acumuladorCpu + id;

        while (System.currentTimeMillis() < fim) {
            if (Thread.currentThread().isInterrupted()) {
                acumuladorCpu = valor;
                throw new InterruptedException();
            }

            for (int i = 0; i < CPU_ITERACOES; i++) {
                valor = Math.sin(valor + i * 0.0001);
            }

            long agora = System.currentTimeMillis();
            if (agora >= proximoQuadro) {
                avancarQuadro(estadoAtual);
                proximoQuadro = agora + QUADRO_MS;
            }
        }

        acumuladorCpu = valor;
    }

    /*
     * Esta e a unica funcao que altera a posicao da crianca. Ela roda dentro
     * da propria thread da crianca, inclusive enquanto a thread faz polling
     * do cesto.
     */
    private void atualizarMovimento(EstadoCrianca estadoAtual) throws InterruptedException {
        avancarQuadro(estadoAtual);
        Thread.sleep(QUADRO_MS);
    }

    private void avancarQuadro(EstadoCrianca estadoAtual) {
        double alvoX;
        double alvoY;

        switch (estadoAtual) {
            case ESPERANDO_BOLA:
                alvoX = alvoCestoBaixoX();
                alvoY = alvoCestoBaixoY();
                break;
            case ESPERANDO_ESPACO:
                alvoX = alvoCestoAltoX();
                alvoY = alvoCestoAltoY();
                break;
            case DESCANSANDO:
                if (distancia(x, y, alvoDescansoX, alvoDescansoY) < 10) {
                    escolherNovoAlvoDescanso();
                }
                alvoX = alvoDescansoX;
                alvoY = alvoDescansoY;
                break;
            case BRINCANDO:
            default:
                if (distancia(x, y, alvoBrincadeiraX, alvoBrincadeiraY) < 8) {
                    escolherNovoAlvoBrincadeira();
                }
                alvoX = alvoBrincadeiraX;
                alvoY = alvoBrincadeiraY;
                break;
        }

        moverAte(alvoX, alvoY);
        passoAnimacao += 0.18;
        notificarMudancaVisual();
    }

    private void moverAte(double alvoX, double alvoY) {
        double dx = alvoX - x;
        double dy = alvoY - y;
        double[] repulsao = calcularRepulsao();
        dx += repulsao[0];
        dy += repulsao[1];
        double distancia = Math.sqrt(dx * dx + dy * dy);

        if (distancia <= 0.01) {
            return;
        }

        double passo = Math.min(VELOCIDADE, distancia);
        olhandoParaDireita = dx >= 0;
        x = limitar(x + dx / distancia * passo, 12, Math.max(12, larguraCena - 92));
        y = limitar(y + dy / distancia * passo, 30, Math.max(30, alturaCena - 108));
    }

    private void escolherNovoAlvoBrincadeira() {
        int larguraUtil = Math.max(260, larguraCena - 260);
        int alturaUtil = Math.max(160, alturaCena - 140);
        alvoBrincadeiraX = 60 + random.nextInt(larguraUtil);
        alvoBrincadeiraY = 80 + random.nextInt(alturaUtil);
    }

    private void escolherNovoAlvoDescanso() {
        int larguraUtil = Math.max(260, larguraCena - 180);
        int alturaUtil = Math.max(160, alturaCena - 150);
        alvoDescansoX = 40 + random.nextInt(larguraUtil);
        alvoDescansoY = 70 + random.nextInt(alturaUtil);
    }

    private double[] calcularRepulsao() {
        double repulsaoX = 0;
        double repulsaoY = 0;

        for (Crianca outra : criancasNoParque) {
            if (outra == this) {
                continue;
            }

            double dx = x - outra.getX();
            double dy = y - outra.getY();
            double distancia = Math.sqrt(dx * dx + dy * dy);

            if (distancia > 0.01 && distancia < DISTANCIA_MINIMA) {
                double forca = (DISTANCIA_MINIMA - distancia) / DISTANCIA_MINIMA;
                repulsaoX += dx / distancia * forca * 9.0;
                repulsaoY += dy / distancia * forca * 9.0;
            } else if (distancia <= 0.01 && outra.getId() < id) {
                repulsaoX += 5.0;
                repulsaoY += 3.0;
            }
        }

        return new double[] { repulsaoX, repulsaoY };
    }

    private double limitar(double valor, double minimo, double maximo) {
        return Math.max(minimo, Math.min(maximo, valor));
    }

    private double distancia(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private boolean estaPertoDoCesto() {
        double alvoX = alvoCestoBaixoX();
        double alvoY = alvoCestoBaixoY();
        return distancia(x, y, alvoX, alvoY) < DISTANCIA_ACAO_CESTO;
    }

    private boolean estaPertoDoPontoDeDevolucao() {
        double alvoX = alvoCestoAltoX();
        double alvoY = alvoCestoAltoY();
        return distancia(x, y, alvoX, alvoY) < DISTANCIA_ACAO_CESTO;
    }

    private double alvoCestoBaixoX() {
        return cestoX + 10 + ((id - 1) % 4) * 30;
    }

    private double alvoCestoBaixoY() {
        return cestoY + 118 + ((id - 1) / 4 % 3) * 24;
    }

    private double alvoCestoAltoX() {
        return cestoX + 6 + ((id - 1) % 4) * 30;
    }

    private double alvoCestoAltoY() {
        return Math.max(36, cestoY - 90 - ((id - 1) / 4 % 3) * 22);
    }

    private void mudarEstado(EstadoCrianca novoEstado) {
        this.estado = novoEstado;
        notificarMudancaVisual();
    }

    private void notificarMudancaVisual() {
        if (estadoListener != null) {
            estadoListener.estadoMudou(this);
        }
    }

    public void configurarCena(int largura, int altura, int cestoX, int cestoY) {
        this.larguraCena = Math.max(320, largura);
        this.alturaCena = Math.max(220, altura);
        this.cestoX = cestoX;
        this.cestoY = cestoY;
    }

    public void setCriancasNoParque(List<Crianca> criancas) {
        this.criancasNoParque = criancas == null ? Collections.emptyList() : criancas;
    }

    public int getId() {
        return id;
    }

    public EstadoCrianca getEstado() {
        return estado;
    }

    public boolean possuiBola() {
        return possuiBola;
    }

    public int getContagemBrincadeiras() {
        return contagemBrincadeiras;
    }

    public long getTempoDebrincadeira() {
        return tempoDebrincadeira;
    }

    public long getTempoDescanso() {
        return tempoDescanso;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public boolean isOlhandoParaDireita() {
        return olhandoParaDireita;
    }

    public double getPassoAnimacao() {
        return passoAnimacao;
    }

    public void setEstadoListener(EstadoListener listener) {
        this.estadoListener = listener;
    }

    @Override
    public String toString() {
        return String.format("Crianca %d - Estado: %s - Bola: %s - Brincadeiras: %d",
                             id, estado, possuiBola, contagemBrincadeiras);
    }
}
