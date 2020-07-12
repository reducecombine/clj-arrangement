(ns arrangement.core
  "This namespace provides a total-ordering comparator for Clojure values.")


(def ^:private type-predicates
  "Ordered sequence of predicates to test to determine the relative ordering of
  various data types."
  [nil?
   false?
   true?
   number?
   char?
   string?
   keyword?
   symbol?
   list?
   vector?
   set?
   map?])


(defn- type-priority
  "Determines a numeric priority for the given value based on its general
  type. See `type-predicates` for the ordering."
  [x]
  (loop [i 0]
    (if (< i (count type-predicates))
      (let [p (nth type-predicates i)]
        (if (p x)
          i
          (recur (inc i))))
      i)))


(defn- directly-comparable?
  "True if the values in a certain priority class are directly comparable."
  [p]
  (<= 3 p 7))


(defn- type-name
  "Get the type of the given object as a string. For Clojure, gets the name of
  the class of the object. For ClojureScript, gets either the `name` attribute
  or the protocol name if the `name` attribute doesn't exist."
  [x]
  #?(:clj (.getName (class x))
     :cljs (let [t (type x)
                 n (.-name t)]
             (if (empty? n)
               (pr-str t)
               n))))


(declare rank)


(defn- compare-seqs
  "Compare sequences using the given comparator. If any element of the
  sequences orders differently, it determines the ordering. Otherwise, if the
  prefix matches, the longer sequence sorts later."
  [xs ys]
  (loop [xs xs
         ys ys]
    (if (and (seq xs) (seq ys))
      (let [x (first xs)
            y (first ys)
            o (rank x y)]
        (if (zero? o)
          (recur (next xs) (next ys))
          o))
      (- (count xs) (count ys)))))


(defn rank
  "Comparator function that provides a total ordering of EDN values. Values of
  different types sort in order of their types, per `type-priority`. `false`
  is before `true`, numbers are ordered by magnitude regardless of type, and
  characters, strings, keywords, and symbols are ordered lexically.

  Sequential collections are sorted by comparing their elements one at a time.
  If the sequences have equal leading elements, the longer one is ordered later.
  Sets are compared by cardinality first, then elements in sorted order.
  Finally, maps are compared by their entries in sorted order of their keys.

  All other types are sorted by class name. If the class implements
  `Comparable`, the instances of it are compared using `compare`. Otherwise, the
  values are ordered by print representation. This has the default behavior of
  ordering by hash code if the type does not implement a custom print format."
  [a b]
  (if (identical? a b)
    0
    (let [pri-a (type-priority a)
          pri-b (type-priority b)]
      (cond
        (< pri-a pri-b) -1
        (> pri-a pri-b)  1

        (directly-comparable? pri-a)
        (compare a b)

        (map? a)
        (compare-seqs
          (sort-by key rank (seq a))
          (sort-by key rank (seq b)))

        (set? a)
        (let [size-diff (- (count a) (count b))]
          (if (zero? size-diff)
            (compare-seqs (sort a) (sort b))
            size-diff))

        (coll? a)
        (compare-seqs a b)

        :else
        (let [class-diff (compare (type-name a) (type-name b))]
          (if (zero? class-diff)
            #?(:clj (if (instance? java.lang.Comparable a)
                      (compare a b)
                      (compare (str a) (str b)))
               :cljs (compare a b))
            class-diff))))))
