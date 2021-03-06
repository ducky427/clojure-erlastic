clojure-erlastic
================

![Clojars Project](http://clojars.org/clojure-erlastic/latest-version.svg)

Micro lib making use of erlang JInterface lib to decode and encode Binary
Erlang Term and simple erlang port interface with core.async channel. So you
can communicate with erlang coroutine with clojure abstraction

Designed to be used (but not necessarily) with
[https://github.com/awetzel/exos](https://github.com/awetzel/exos).

Last version of JInterface (from erlang 17.0) is taken from google scalaris
maven repo.

## Usage

`port-connection` creates two channels that you can use to
communicate respectively in and out with the calling erlang port.
The objects you put or receive throught these channels are encoded
and decoded into erlang binary term following these rules :

- erlang atom is clojure keyword
- erlang list is clojure list
- erlang tuple is clojure vector
- erlang binary is clojure bytes[]
- erlang integer is clojure int
- erlang long is clojure long
- erlang float is clojure double
- erlang map is clojure map (thanks to erlang 17.0)
- clojure set is erlang list

Conversion of nil and string are configurable : every functions
`port-connection`, `decode`, `encode`, `run-server` can take an optional
`config` argument : a map defining 3 configs `:convention`, `:str-detect`, `:str-autodetect-len`.

- if `(= :convention :elixir)` then :
  - clojure nil is erlang `nil` atom, so elixir `nil`
  - clojure string is encoded into erlang utf8 binary
  - erlang binaries are decoded into clojure string :
    - always if `(= :str-detect :all)`
    - never if `(= :str-detect :none)`
    - if the "str-autodetect-len" first bytes are printable when `(= :str-detect :auto)`
- if `(= :convention :erlang)` then :
  - clojure nil is erlang `undefined`
  - clojure string is encoded into erlang integer list
  - erlang lists are decoded into clojure string :
    - always if `(= :str-detect :all)`
    - never if `(= :str-detect :none)`
    - if the "str-autodetect-len" first elems are printable when `(= :str-detect :auto)`

- default config is Elixir convention with no str detection.

For instance, here is a simple echo server :

```clojure
(let [[in out] (clojure-erlastic.core/port-connection)]
  (<!! (go (while true
    (>! out (<! in))))))
```

## Example : a simple clojure calculator ##

My advice to create a simple erlang/elixir server in clojure is to create a `project.clj` containing the clojure-erlastic dependency and other needed deps for your server, then use "lein uberjar" to create a jar containing all the needed files.

> mkdir calculator; cd calculator

> vim project.clj

```clojure
(defproject calculator "0.0.1"
  :dependencies [[clojure-erlastic "0.1.4"]
                 [org.clojure/core.match "0.2.1"]])
```

> lein uberjar

Then create your clojure server as a simple script

> vim calculator.clj

```clojure
(require '[clojure.core.async :as async :refer [<! >! <!! go]])
(require '[clojure-erlastic.core :refer [port-connection log]])
(use '[clojure.core.match :only (match)])

(let [[in out] (clojure-erlastic.core/port-connection)]
  (<!! (go
    (loop [num 0]
      (match (<! in)
        [:add n] (recur (+ num n))
        [:rem n] (recur (- num n))
        :get (do (>! out num) (recur num)))))))
```

Finally launch the clojure server as a port, do not forget the `:binary` and `{:packet,4}` options, mandatory, then convert sent and received terms with `:erlang.binary_to_term` and `:erlang.term_to_binary`.

> vim calculator.exs

```elixir
defmodule CljPort do
  def start, do:
    Port.open({:spawn,'java -cp target/calculator-0.0.1-standalone.jar clojure.main calculator.clj'},[:binary, packet: 4])
  def psend(port,data), do:
    send(port,{self,{:command,:erlang.term_to_binary(data)}})
  def preceive(port), do:
    receive(do: ({^port,{:data,b}}->:erlang.binary_to_term(b)))
end
port = CljPort.start
CljPort.psend(port, {:add,3})
CljPort.psend(port, {:rem,2})
CljPort.psend(port, {:add,5})
CljPort.psend(port, :get)
6 = CljPort.preceive(port)
```

> elixir calculator.exs

## OTP integration ##

If you want to integrate your clojure server in your OTP application, use the
`priv` directory which is copied 'as is'.

```bash
mix new myapp ; cd myapp
mkdir -p priv/calculator
vim priv/calculator/project.clj # define dependencies
vim priv/calculator/calculator.clj # write your server
cd priv/calculator ; lein uberjar ; cd ../../ # build the jar
```

Then use `"#{:code.priv_dir(:myapp)}/calculator"` to find correct path in your app.

To easily use your clojure server, link the opened port in a GenServer, to
ensure that if java crash, then the genserver crash and can be restarted by its
supervisor.

> vim lib/calculator.ex

```elixir
defmodule Calculator do
  use GenServer
  def start_link, do: GenServer.start_link(__MODULE__, nil, name: __MODULE__)
  def init(nil) do
    Process.flag(:trap_exit, true)
    cd = "#{:code.priv_dir(:myapp)}/calculator"
    cmd = "java -cp 'target/*' clojure.main calculator.clj"
    {:ok,Port.open({:spawn,'#{cmd}'},[:binary, packet: 4, cd: cd])}
  end
  def handle_info({:EXIT,port,_},port), do: exit(:port_terminated)

  def handle_cast(term,port) do
    send(port,{self,{:command,:erlang.term_to_binary(term)}})
    {:noreply,port}
  end

  def handle_call(term,_,port) do
    send(port,{self,{:command,:erlang.term_to_binary(term)}})
    result = receive do {^port,{:data,b}}->:erlang.binary_to_term(b) end
    {:reply,result,port}
  end
end
```

Then create the OTP application and its root supervisor launching `Calculator`.

> vim mix.exs

```elixir
  def application do
    [mod: { Myapp, [] },
     applications: []]
  end
```

> vim lib/myapp.ex

```elixir
defmodule Myapp do
  use Application
  def start(_type, _args), do: Myapp.Sup.start_link

  defmodule Sup do
    use Supervisor
    def start_link, do: :supervisor.start_link(__MODULE__,nil)
    def init(nil), do:
      supervise([worker(Calculator,[])], strategy: :one_for_one)
  end
end
```

Then you can launch and test your application in the shell :

```
iex -S mix
iex(1)> GenServer.call Calculator,:get
0
iex(2)> GenServer.cast Calculator,{:add, 3}
:ok
iex(3)> GenServer.cast Calculator,{:add, 3}
:ok
iex(4)> GenServer.cast Calculator,{:add, 3}
:ok
iex(5)> GenServer.cast Calculator,{:add, 3}
:ok
iex(6)> GenServer.call Calculator,:get
12
```
## Handle exit

The channels are closed when the launching erlang application dies, so you just
have to test if `(<! in)` is `nil` to know if the connection with erlang is
still opened.

## Erlang style handler ##

In Java you cannot write a function as big as you want (the compiler may fail),
and the `go` and `match` macros expand into a lot of code. So it can be
useful to wrap your server with an "erlang-style" handler.

Clojure-erlastic provide the function `(run-server initfun handlefun)`
allowing you to easily develop a server using erlang-style handler :

- the `init` function must return the initial state
- the `handle` function must return `[:reply response newstate]`, or `[:noreply newstate]`

The argument of the init function is the first message sent by the erlang port
after starting.

```clojure
(require '[clojure.core.async :as async :refer [<! >! <!! go]])
(require '[clojure-erlastic.core :refer [run-server log]])
(use '[clojure.core.match :only (match)])

(run-server
  (fn [_] 0)
  (fn [term state] (match term
    [:add n] [:noreply (+ state n)]
    [:rem n] [:noreply (- state n)]
    :get [:reply state state]))
  {:convention :erlang})

(log "end application, clean if necessary")
```
