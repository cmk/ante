#pragma once

#include <type_traits>
#include <utility>

namespace detail
{

template<typename F>
struct ScopeExitRunner
{
	ScopeExitRunner(const F & run)
	try : run(run)
	{
	}
	catch(...)
	{
		// if the copy constructor for to_run threw,
		// call the argument immediately then rethrow
		run();
	}
	ScopeExitRunner(F && run)
	try : run(std::move(run))
	{
	}
	catch(...)
	{
		// if the move constructor for to_run threw,
		// call the argument immediately then rethrow
		run();
	}
	ScopeExitRunner(ScopeExitRunner && other)
		: run(std::move(other.run))
	{
		other.should_run = false;
	}
	~ScopeExitRunner()
	{
		if (should_run) run();
	}

	void run_early()
	{
		should_run = false;
		run();
	}

private:
	F run;
	bool should_run = true;
};

} // end namespace detail


template<typename T>
detail::ScopeExitRunner<typename std::decay<T>::type> AtScopeExit(T && to_run)
{
	return { std::forward<T>(to_run) };
}
