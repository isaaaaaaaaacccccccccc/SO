import java.util.concurrent.Semaphore;

public class Cesto {
    private int capacidade;
    private int bolasAtuais;
    
    // Semáforos para controle de concorrência
    private Semaphore semaforoBolasDisponiveis;  // Quantas bolas podem ser pegadas
    private Semaphore semafotoEspacoDisponivel;   // Quantos espaços estão vazios
    private Semaphore mutex;                      // Controla acesso à variável bolasAtuais
    
    // Callback para atualizar UI
    private Runnable onAtualizacao;
    
    public Cesto(int capacidade) {
        if (capacidade <= 0) {
            throw new IllegalArgumentException("A capacidade do cesto deve ser maior que zero.");
        }

        this.capacidade = capacidade;
        this.bolasAtuais = 0;
        
        // Semaforo de bolas: cesto sempre inicia com 0 bolas para pegar
        this.semaforoBolasDisponiveis = new Semaphore(0);
        
        // Semaforo de espaco: inicialmente toda a capacidade esta livre
        this.semafotoEspacoDisponivel = new Semaphore(capacidade);
        
        // Mutex para proteção da variável compartilhada
        this.mutex = new Semaphore(1);
    }
    
    /**
     * Criança tenta pegar uma bola do cesto.
     * Bloqueia se não houver bolas disponíveis.
     */
    public void pegarBola() throws InterruptedException {
        // Aguarda até que uma bola ficar disponível
        semaforoBolasDisponiveis.acquire();
        
        // Seção crítica: atualiza contador de bolas
        mutex.acquire();
        try {
            bolasAtuais--;
            notificarAtualizacao();
        } finally {
            mutex.release();
        }
        
        // Libera espaço no cesto (agora há um espaço vazio)
        semafotoEspacoDisponivel.release();
    }
    
    /**
     * Criança tenta devolver uma bola ao cesto.
     * Bloqueia se o cesto estiver cheio.
     */
    public void devolverBola() throws InterruptedException {
        // Aguarda até que haja espaço disponível no cesto
        semafotoEspacoDisponivel.acquire();
        
        // Seção crítica: atualiza contador de bolas
        mutex.acquire();
        try {
            bolasAtuais++;
            notificarAtualizacao();
        } finally {
            mutex.release();
        }
        
        // Libera uma bola (um semáforo que pode ser pegue)
        semaforoBolasDisponiveis.release();
    }
    
    /**
     * Versao nao bloqueante usada pela animacao: a thread da crianca pode
     * tentar pegar uma bola e continuar se movendo enquanto espera.
     */
    public boolean tentarPegarBola() throws InterruptedException {
        if (!semaforoBolasDisponiveis.tryAcquire()) {
            return false;
        }

        mutex.acquire();
        try {
            bolasAtuais--;
            notificarAtualizacao();
        } finally {
            mutex.release();
        }

        semafotoEspacoDisponivel.release();
        return true;
    }

    /**
     * Versao nao bloqueante usada pela animacao: a thread da crianca pode
     * tentar devolver uma bola e continuar se movendo enquanto espera.
     */
    public boolean tentarDevolverBola() throws InterruptedException {
        if (!semafotoEspacoDisponivel.tryAcquire()) {
            return false;
        }

        mutex.acquire();
        try {
            bolasAtuais++;
            notificarAtualizacao();
        } finally {
            mutex.release();
        }

        semaforoBolasDisponiveis.release();
        return true;
    }

    /**
     * Retorna a quantidade atual de bolas no cesto (com thread-safety).
     */
    public int getBolasAtuais() throws InterruptedException {
        mutex.acquire();
        try {
            return bolasAtuais;
        } finally {
            mutex.release();
        }
    }
    
    public int getCapacidade() {
        return capacidade;
    }
    
    public void setOnAtualizacao(Runnable callback) {
        this.onAtualizacao = callback;
    }
    
    private void notificarAtualizacao() {
        if (onAtualizacao != null) {
            onAtualizacao.run();
        }
    }
}

